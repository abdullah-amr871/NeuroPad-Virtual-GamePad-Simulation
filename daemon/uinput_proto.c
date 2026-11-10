/*
 * uinput_proto.c - NeuroPad UHID gamepad daemon
 *
 * A small static binary that the NeuroPad app runs as the Android shell user (through
 * its loopback ADB connection) or as root. The shell user may open /dev/uhid, a normal
 * app may not. The daemon speaks the same line based JSON protocol as AOSP's
 * /system/bin/hid:
 *
 *   stdin  (app -> daemon)
 *     {"command":"register","name":"...","vid":N,"pid":N,"bus":"usb|bluetooth","descriptor":[...]}
 *     {"command":"report","report":[...]}
 *     {"command":"get_report_reply","id":N,"err":N,"report":[...]}
 *     {"command":"set_report_reply","id":N,"err":N}
 *
 *   stdout (daemon -> app)
 *     {"event":"ready","version":"2.0.0"}                 once, at start
 *     {"event":"registered"}                              virtual device created
 *     {"event":"error","message":"..."}                   fatal problem
 *     {"event":"output","id":1,"reportData":[...]}        rumble / LED output report
 *     {"event":"get_report","id":N,"reportId":N,"reportType":N}
 *     {"event":"set_report","id":N,"reportId":N,"reportType":N,"reportData":[...]}
 *
 * Diagnostics go to /data/local/tmp/uhid_err.log.
 *
 * Build (static arm64 binary, no NDK needed):
 *   zig cc -target aarch64-linux-musl -static -O2 -s -o uinput_proto uinput_proto.c
 */
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <linux/input.h>
#include <linux/uhid.h>

#include "json_mini.h"

#define NEUROPAD_DAEMON_VERSION "2.0.0"
#define LINE_MAX_LEN 16384
#define VID_MICROSOFT 0x045E

static int uhid_fd = -1;
static int dev_created = 0;
static int parsed_vid = 0x054C;
static int parsed_pid = 0x0CE6;
static int parsed_bus = BUS_USB;
static char parsed_name[128] = "NeuroPad Virtual Controller";
static uint8_t parsed_desc[HID_MAX_DESCRIPTOR_SIZE];
static int parsed_desc_len = 0;
static volatile sig_atomic_t running = 1;

static void on_signal(int sig) {
    (void)sig;
    running = 0;
}

/* ------------------------------------------------------------------ helpers */

static int command_is(const char *line, const char *name) {
    char value[32];
    return get_string(line, "command", value, sizeof(value)) && strcmp(value, name) == 0;
}

static void emit_error(const char *what) {
    printf("{\"event\":\"error\",\"message\":\"%s: %s\"}\n", what, strerror(errno));
    fflush(stdout);
    fprintf(stderr, "[uinput_proto] %s: %s (errno=%d)\n", what, strerror(errno), errno);
}

static int write_event(const struct uhid_event *ev, const char *what) {
    ssize_t ret = write(uhid_fd, ev, sizeof(*ev));
    if (ret < 0) {
        fprintf(stderr, "[uinput_proto] %s failed: %s (errno=%d)\n", what, strerror(errno), errno);
        return -1;
    }
    return 0;
}

/* ------------------------------------------------------------------ device setup */

/*
 * Descriptor fix-ups for the Microsoft profiles, so that Android's key layouts map the
 * sticks and triggers the way games expect. These are unchanged from NeuroPad 1.0.
 */
static void patch_microsoft_descriptor(void) {
    /* 1. Xbox 360: triggers Z/Rz -> Simulation Controls Brake/Accelerator (ABS_BRAKE/ABS_GAS). */
    for (int i = 0; i < parsed_desc_len - 3; i++) {
        if (parsed_desc[i] == 0x09 && parsed_desc[i + 1] == 0x32 &&
            parsed_desc[i + 2] == 0x09 && parsed_desc[i + 3] == 0x35) {
            if (parsed_desc_len + 4 > (int)sizeof(parsed_desc)) break;
            memmove(&parsed_desc[i + 8], &parsed_desc[i + 4], (size_t)(parsed_desc_len - (i + 4)));
            parsed_desc[i] = 0x05;     /* Usage Page */
            parsed_desc[i + 1] = 0x02; /*   Simulation Controls */
            parsed_desc[i + 2] = 0x09; /* Usage */
            parsed_desc[i + 3] = 0xC5; /*   Brake */
            parsed_desc[i + 4] = 0x09; /* Usage */
            parsed_desc[i + 5] = 0xC4; /*   Accelerator */
            parsed_desc[i + 6] = 0x05; /* Usage Page */
            parsed_desc[i + 7] = 0x01; /*   Generic Desktop */
            parsed_desc_len += 4;
            fprintf(stderr, "[uinput_proto] Xbox 360 triggers mapped to Brake/Accelerator\n");
            break;
        }
    }
    /* 2. Right stick Rx/Ry -> Z/Rz. */
    for (int i = 0; i < parsed_desc_len - 7; i++) {
        if (parsed_desc[i] == 0x09 && parsed_desc[i + 1] == 0x30 &&
            parsed_desc[i + 2] == 0x09 && parsed_desc[i + 3] == 0x31 &&
            parsed_desc[i + 4] == 0x09 && parsed_desc[i + 5] == 0x33 &&
            parsed_desc[i + 6] == 0x09 && parsed_desc[i + 7] == 0x34) {
            parsed_desc[i + 5] = 0x32;
            parsed_desc[i + 7] = 0x35;
            fprintf(stderr, "[uinput_proto] Xbox right stick mapped to Z/Rz\n");
        }
    }
    /* 3. Bluetooth profile: Throttle -> Brake, otherwise Android takes screenshots on LT. */
    for (int i = 0; i < parsed_desc_len - 3; i++) {
        if (parsed_desc[i] == 0x09 && parsed_desc[i + 1] == 0xC3 &&
            parsed_desc[i + 2] == 0x09 && parsed_desc[i + 3] == 0xC4) {
            parsed_desc[i + 1] = 0xC5;
            fprintf(stderr, "[uinput_proto] Xbox Bluetooth triggers mapped to Brake/Accelerator\n");
        }
    }
}

static void destroy_device(void) {
    if (uhid_fd < 0) return;
    if (dev_created) {
        struct uhid_event ev;
        memset(&ev, 0, sizeof(ev));
        ev.type = UHID_DESTROY;
        write_event(&ev, "UHID_DESTROY");
        dev_created = 0;
    }
    close(uhid_fd);
    uhid_fd = -1;
}

static int create_device(void) {
    destroy_device(); /* a second "register" replaces the previous device */

    if (parsed_desc_len <= 0) {
        errno = EINVAL;
        emit_error("register without a descriptor");
        return -1;
    }
    uhid_fd = open("/dev/uhid", O_RDWR | O_CLOEXEC | O_NONBLOCK);
    if (uhid_fd < 0) {
        emit_error("cannot open /dev/uhid");
        return -1;
    }
    if (parsed_vid == VID_MICROSOFT) patch_microsoft_descriptor();

    struct uhid_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = UHID_CREATE2;
    strncpy((char *)ev.u.create2.name, parsed_name, sizeof(ev.u.create2.name) - 1);
    /*
     * create2.uniq is left empty on purpose. With a valid MAC address the kernel's Sony
     * drivers would bind to the DualShock 4 profile and expect Sony's own report layout,
     * which this virtual device does not use.
     */
    ev.u.create2.rd_size = (uint16_t)parsed_desc_len;
    ev.u.create2.bus = (uint16_t)parsed_bus;
    ev.u.create2.vendor = (uint32_t)parsed_vid;
    ev.u.create2.product = (uint32_t)parsed_pid;
    memcpy(ev.u.create2.rd_data, parsed_desc, (size_t)parsed_desc_len);

    if (write(uhid_fd, &ev, sizeof(ev)) < 0) {
        emit_error("UHID_CREATE2 failed");
        close(uhid_fd);
        uhid_fd = -1;
        return -1;
    }
    dev_created = 1;
    fprintf(stderr, "[uinput_proto] registered %s (VID=0x%04X PID=0x%04X BUS=0x%04X DESC_LEN=%d)\n",
            parsed_name, parsed_vid, parsed_pid, parsed_bus, parsed_desc_len);
    printf("{\"event\":\"registered\"}\n");
    fflush(stdout);
    return 0;
}

/* ------------------------------------------------------------------ stdin commands */

static void handle_register(const char *line) {
    char bus[16];
    parsed_vid = get_int(line, "vid", parsed_vid);
    parsed_pid = get_int(line, "pid", parsed_pid);
    /* Only the value of "bus" is inspected. 1.0 searched the whole rest of the line. */
    if (get_string(line, "bus", bus, sizeof(bus))) {
        parsed_bus = strcmp(bus, "bluetooth") == 0 ? BUS_BLUETOOTH : BUS_USB;
    }
    get_string(line, "name", parsed_name, sizeof(parsed_name));
    parsed_desc_len = get_int_array(line, "descriptor", parsed_desc, (int)sizeof(parsed_desc) - 8);
    create_device();
}

static void handle_report(const char *line) {
    static uint8_t report[UHID_DATA_MAX];
    memset(report, 0, sizeof(report));
    int len = get_int_array(line, "report", report, (int)sizeof(report));
    if (len <= 0) len = get_int_array(line, "data", report, (int)sizeof(report));
    if (len <= 0) return;

    if (parsed_vid == VID_MICROSOFT && len >= 10) {
        /* The analog triggers already report LT/RT; the digital copies (bits 0-1 of byte 9)
         * would be seen as extra buttons. Bits 6-7 of byte 8 are LB/RB and must stay. */
        report[9] &= (uint8_t)~0x03;
    }
    int size = len;
    if (parsed_vid == VID_MICROSOFT && (parsed_pid == 0x02FD || parsed_pid == 0x0B13) && size < 16) {
        size = 16; /* the Bluetooth Xbox drivers expect at least 16 bytes */
    }

    struct uhid_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = UHID_INPUT; /* same event type as 1.0; every Android kernel accepts it */
    ev.u.input.size = (uint16_t)size;
    memcpy(ev.u.input.data, report, (size_t)size);
    write_event(&ev, "UHID_INPUT");
}

static void handle_get_report_reply(const char *line) {
    static uint8_t report[UHID_DATA_MAX];
    memset(report, 0, sizeof(report));
    int len = get_int_array(line, "report", report, (int)sizeof(report));
    /* The app historically called the field "error"; the daemon looked for "err". */
    int err = get_int(line, "err", get_int(line, "error", 0));

    struct uhid_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = UHID_GET_REPORT_REPLY;
    ev.u.get_report_reply.id = (uint32_t)get_int(line, "id", 0);
    ev.u.get_report_reply.err = (uint16_t)err;
    ev.u.get_report_reply.size = (uint16_t)len;
    memcpy(ev.u.get_report_reply.data, report, (size_t)len);
    write_event(&ev, "UHID_GET_REPORT_REPLY");
}

static void handle_set_report_reply(const char *line) {
    struct uhid_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = UHID_SET_REPORT_REPLY;
    ev.u.set_report_reply.id = (uint32_t)get_int(line, "id", 0);
    ev.u.set_report_reply.err = (uint16_t)get_int(line, "err", get_int(line, "error", 0));
    write_event(&ev, "UHID_SET_REPORT_REPLY");
}

static void handle_line(const char *line) {
    if (command_is(line, "register")) {
        handle_register(line);
        return;
    }
    if (!dev_created || uhid_fd < 0) return;
    if (command_is(line, "report")) handle_report(line);
    else if (command_is(line, "get_report_reply")) handle_get_report_reply(line);
    else if (command_is(line, "set_report_reply")) handle_set_report_reply(line);
}

/* ------------------------------------------------------------------ kernel events */

static void print_bytes(const uint8_t *data, int size, int min_size) {
    int n = size < min_size ? min_size : size;
    for (int i = 0; i < n; i++) {
        printf(i ? ",%d" : "%d", i < size ? data[i] : 0);
    }
}

static void handle_uhid_event(const struct uhid_event *ev) {
    switch (ev->type) {
    case UHID_OUTPUT: {
        int size = ev->u.output.size;
        if (size <= 0 || size > UHID_DATA_MAX) break;
        printf("{\"event\":\"output\",\"id\":1,\"reportData\":[");
        print_bytes(ev->u.output.data, size, 16); /* padded so the app can index freely */
        printf("]}\n");
        break;
    }
    case UHID_GET_REPORT:
        printf("{\"event\":\"get_report\",\"id\":%u,\"reportId\":%u,\"reportType\":%u}\n",
               ev->u.get_report.id, ev->u.get_report.rnum, ev->u.get_report.rtype);
        break;
    case UHID_SET_REPORT: {
        int size = ev->u.set_report.size;
        if (size < 0) size = 0;
        if (size > UHID_DATA_MAX) size = UHID_DATA_MAX;
        printf("{\"event\":\"set_report\",\"id\":%u,\"reportId\":%u,\"reportType\":%u,\"reportData\":[",
               ev->u.set_report.id, ev->u.set_report.rnum, ev->u.set_report.rtype);
        print_bytes(ev->u.set_report.data, size, 0);
        printf("]}\n");
        break;
    }
    default:
        return; /* START / STOP / OPEN / CLOSE need no answer */
    }
    if (fflush(stdout) != 0) running = 0; /* the app is gone */
}

/* ------------------------------------------------------------------ main loop */

int main(int argc, char **argv) {
    if (argc > 1 && strcmp(argv[1], "--version") == 0) {
        printf("%s\n", NEUROPAD_DAEMON_VERSION);
        return 0;
    }

    int err_fd = open("/data/local/tmp/uhid_err.log", O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (err_fd >= 0) {
        dup2(err_fd, 2);
        close(err_fd);
    }
    signal(SIGINT, on_signal);
    signal(SIGTERM, on_signal);
    signal(SIGHUP, on_signal);
    signal(SIGPIPE, SIG_IGN); /* a closed stdout is detected through fflush() instead */
    setvbuf(stdout, NULL, _IOFBF, 8192);

    fprintf(stderr, "[uinput_proto] %s started, waiting for registration\n", NEUROPAD_DAEMON_VERSION);
    printf("{\"event\":\"ready\",\"version\":\"%s\"}\n", NEUROPAD_DAEMON_VERSION);
    fflush(stdout);

    static char line[LINE_MAX_LEN];
    int line_len = 0;
    int skipping = 0; /* inside an over-long line that is being discarded */

    while (running) {
        struct pollfd pfds[2];
        pfds[0].fd = STDIN_FILENO;
        pfds[0].events = POLLIN;
        pfds[0].revents = 0;
        pfds[1].fd = uhid_fd;
        pfds[1].events = POLLIN;
        pfds[1].revents = 0;

        if (poll(pfds, uhid_fd >= 0 ? 2 : 1, -1) < 0) {
            if (errno == EINTR) continue;
            break;
        }

        /* POLLHUP / POLLERR without POLLIN must end the loop too; 1.0 only looked at
         * POLLIN and could spin at 100 % CPU after the app side went away. */
        if (pfds[0].revents & (POLLIN | POLLHUP | POLLERR | POLLNVAL)) {
            char chunk[4096];
            ssize_t r = read(STDIN_FILENO, chunk, sizeof(chunk));
            if (r < 0 && (errno == EINTR || errno == EAGAIN)) continue;
            if (r <= 0) {
                fprintf(stderr, "[uinput_proto] stdin closed, exiting\n");
                break;
            }
            for (ssize_t i = 0; i < r; i++) {
                char c = chunk[i];
                if (c == '\n' || c == '\r') {
                    if (!skipping && line_len > 0) {
                        line[line_len] = '\0';
                        handle_line(line);
                    }
                    line_len = 0;
                    skipping = 0;
                } else if (!skipping) {
                    if (line_len < LINE_MAX_LEN - 1) {
                        line[line_len++] = c;
                    } else {
                        skipping = 1; /* never act on a truncated command */
                        line_len = 0;
                    }
                }
            }
        }

        if (uhid_fd >= 0 && (pfds[1].revents & POLLIN)) {
            struct uhid_event ev;
            while (read(uhid_fd, &ev, sizeof(ev)) > 0) handle_uhid_event(&ev);
        } else if (uhid_fd >= 0 && (pfds[1].revents & (POLLHUP | POLLERR | POLLNVAL))) {
            fprintf(stderr, "[uinput_proto] /dev/uhid went away, exiting\n");
            break;
        }
    }

    destroy_device();
    fprintf(stderr, "[uinput_proto] stopped\n");
    return 0;
}
