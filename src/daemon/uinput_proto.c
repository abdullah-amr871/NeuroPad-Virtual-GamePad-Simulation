/*
 * uinput_proto.c — NeuroPad UHID Gamepad Emulator Daemon
 *
 * This daemon acts as a drop-in replacement for Android's /system/bin/hid.
 * It opens /dev/uhid to create a native User-space HID device using the
 * app's registration descriptors, injects raw report arrays, and reads
 * native force feedback output reports to trigger physical controller rumble.
 *
 * Full report transaction protocol implemented for UHID_GET_REPORT and
 * UHID_SET_REPORT to support strict calibration and pairing requirements of 
 * the Sony kernel drivers (hid-sony and hid-playstation).
 *
 * Compilation:
 *   zig cc -target aarch64-linux-musl -static -o uinput_proto uinput_proto.c
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <signal.h>
#include <poll.h>
#include <sys/ioctl.h>
#include <linux/uhid.h>
#include <time.h>
#include <stdint.h>

static int uhid_fd = -1;
static int dev_created = 0;

static int parsed_vid = 0x054C;
static int parsed_pid = 0x0CE6;
static int parsed_bus = BUS_USB;
static char parsed_name[256] = "Sony Interactive Entertainment DualSense Wireless Controller";
static uint8_t parsed_desc[4096];
static int parsed_desc_len = 0;

static volatile int running = 1;

static void on_signal(int sig) {
    (void)sig;
    running = 0;
}

static int parse_int_array(const char *line, const char *key, uint8_t *out_arr, int max_len) {
    const char *p = strstr(line, key);
    if (!p) return 0;

    p = strchr(p, '[');
    if (!p) return 0;
    p++;

    int count = 0;
    while (*p && *p != ']' && count < max_len) {
        out_arr[count++] = (uint8_t)strtol(p, (char **)&p, 0);
        while (*p == ',' || *p == ' ' || *p == '\r' || *p == '\n') p++;
    }
    return count;
}

static int init_uhid_device(void) {
    if (uhid_fd >= 0) {
        if (dev_created) {
            struct uhid_event destroy_ev;
            memset(&destroy_ev, 0, sizeof(destroy_ev));
            destroy_ev.type = UHID_DESTROY;
            write(uhid_fd, &destroy_ev, sizeof(destroy_ev));
            dev_created = 0;
            fprintf(stderr, "[uinput_proto] Previous UHID Device destroyed.\n");
        }
        close(uhid_fd);
        uhid_fd = -1;
    }

    uhid_fd = open("/dev/uhid", O_RDWR | O_CLOEXEC | O_NONBLOCK);
    if (uhid_fd < 0) {
        fprintf(stderr, "[uinput_proto] Failed to open /dev/uhid: %s (errno=%d)\n",
                strerror(errno), errno);
        return -1;
    }

    if (parsed_vid == 0x045E) {
        // 1. Remap triggers in Xbox 360 mode: Z (0x32) & Rz (0x35) -> Simulation Controls C3 & C4 (ABS_BRAKE / ABS_GAS)
        for (int i = 0; i < parsed_desc_len - 3; i++) {
            if (parsed_desc[i] == 0x09 && parsed_desc[i+1] == 0x32 &&
                parsed_desc[i+2] == 0x09 && parsed_desc[i+3] == 0x35) {
                // Shift descriptor data right by 4 bytes to insert Usage Page changes
                memmove(&parsed_desc[i + 8], &parsed_desc[i + 4], parsed_desc_len - (i + 4));
                parsed_desc[i] = 0x05;     // Usage Page
                parsed_desc[i+1] = 0x02;   //   Simulation Controls
                parsed_desc[i+2] = 0x09;   // Usage
                parsed_desc[i+3] = 0xC5;   //   LTrigger (Brake)
                parsed_desc[i+4] = 0x09;   // Usage
                parsed_desc[i+5] = 0xC4;   //   RTrigger (Accelerator)
                parsed_desc[i+6] = 0x05;   // Usage Page
                parsed_desc[i+7] = 0x01;   //   Restore Generic Desktop
                parsed_desc_len += 4;
                fprintf(stderr, "[uinput_proto] Remapped Xbox 360 triggers to Simulation Controls C3/C4\n");
                break;
            }
        }
        // 2. Remap right stick in Xbox Bt and 360 modes: Rx (0x33) -> Z (0x32), Ry (0x34) -> Rz (0x35)
        for (int i = 0; i < parsed_desc_len - 7; i++) {
            if (parsed_desc[i] == 0x09 && parsed_desc[i+1] == 0x30 &&
                parsed_desc[i+2] == 0x09 && parsed_desc[i+3] == 0x31 &&
                parsed_desc[i+4] == 0x09 && parsed_desc[i+5] == 0x33 &&
                parsed_desc[i+6] == 0x09 && parsed_desc[i+7] == 0x34) {
                parsed_desc[i+5] = 0x32; // Z
                parsed_desc[i+7] = 0x35; // Rz
                fprintf(stderr, "[uinput_proto] Remapped Xbox right stick: Rx->Z (0x32), Ry->Rz (0x35)\n");
            }
        }
        // 3. Remap triggers in Xbox Bt mode: C3 (Throttle) -> C5 (Brake) to prevent screenshot triggers on Android
        for (int i = 0; i < parsed_desc_len - 3; i++) {
            if (parsed_desc[i] == 0x09 && parsed_desc[i+1] == 0xC3 &&
                parsed_desc[i+2] == 0x09 && parsed_desc[i+3] == 0xC4) {
                parsed_desc[i+1] = 0xC5; // Change C3 (Throttle) to C5 (Brake)
                fprintf(stderr, "[uinput_proto] Remapped Xbox Bt triggers: C3->C5 (Brake), C4 (Accelerator)\n");
            }
        }
    }

    struct uhid_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = UHID_CREATE2;
    
    strncpy((char *)ev.u.create2.name, parsed_name, sizeof(ev.u.create2.name) - 1);
    ev.u.create2.rd_size = parsed_desc_len;
    ev.u.create2.bus = parsed_bus;
    ev.u.create2.vendor = parsed_vid;
    ev.u.create2.product = parsed_pid;
    ev.u.create2.version = 0;
    ev.u.create2.country = 0;
    
    if (parsed_desc_len > 0 && parsed_desc_len <= (int)sizeof(ev.u.create2.rd_data)) {
        memcpy(ev.u.create2.rd_data, parsed_desc, parsed_desc_len);
    }

    ssize_t ret = write(uhid_fd, &ev, sizeof(ev));
    if (ret < 0) {
        fprintf(stderr, "[uinput_proto] Failed to write UHID_CREATE2: %s\n", strerror(errno));
        close(uhid_fd);
        uhid_fd = -1;
        return -1;
    }

    dev_created = 1;
    fprintf(stderr, "[uinput_proto] UHID Device registered: %s (VID=0x%04X, PID=0x%04X, BUS=0x%04X, DESC_LEN=%d)\n",
            parsed_name, parsed_vid, parsed_pid, parsed_bus, parsed_desc_len);
    return 0;
}

static void handle_stdin_command(const char *line) {
    if (strstr(line, "\"command\":\"register\"") || strstr(line, "\"command\": \"register\"")) {
        char *p_vid = strstr(line, "\"vid\"");
        if (p_vid) {
            p_vid = strchr(p_vid, ':');
            if (p_vid) parsed_vid = (int)strtol(p_vid + 1, NULL, 10);
        }
        char *p_pid = strstr(line, "\"pid\"");
        if (p_pid) {
            p_pid = strchr(p_pid, ':');
            if (p_pid) parsed_pid = (int)strtol(p_pid + 1, NULL, 10);
        }
        char *p_bus = strstr(line, "\"bus\"");
        if (p_bus) {
            p_bus = strchr(p_bus, ':');
            if (p_bus) {
                if (strstr(p_bus, "usb")) {
                    parsed_bus = BUS_USB;
                } else if (strstr(p_bus, "bluetooth")) {
                    parsed_bus = BUS_BLUETOOTH;
                }
            }
        }
        char *p_name = strstr(line, "\"name\"");
        if (p_name) {
            p_name = strchr(p_name, ':');
            if (p_name) {
                p_name = strchr(p_name, '"');
                if (p_name) {
                    p_name++;
                    char *end = strchr(p_name, '"');
                    if (end && (end - p_name) < 255) {
                        strncpy(parsed_name, p_name, end - p_name);
                        parsed_name[end - p_name] = '\0';
                    }
                }
            }
        }
        
        parsed_desc_len = parse_int_array(line, "\"descriptor\"", parsed_desc, sizeof(parsed_desc));
        init_uhid_device();
        return;
    }

    if (!dev_created || uhid_fd < 0) return;

    if (strstr(line, "\"command\":\"report\"") || strstr(line, "\"command\": \"report\"")) {
        uint8_t report[1024];
        memset(report, 0, sizeof(report));
        int len = parse_int_array(line, "\"report\"", report, sizeof(report));
        if (len <= 0) len = parse_int_array(line, "\"data\"", report, sizeof(report));
        if (len <= 0) return;

        if (parsed_vid == 0x045E && len >= 10) {
            // Clear digital LT/RT button bits in report[9] (bits 0-1) to avoid double-input screenshots
            // NOTE: report[8] bits 6-7 are LB/RB (L1/R1) - do NOT clear those!
            report[9] &= ~0x03;
        }

        struct uhid_event ev;
        memset(&ev, 0, sizeof(ev));
        
        int target_len = len;
        if (parsed_vid == 0x045E && (parsed_pid == 0x02FD || parsed_pid == 0x0B13)) {
            if (len < 16) {
                target_len = 16;
            }
        }

        ev.type = UHID_INPUT;
        ev.u.input.size = target_len;
        memcpy(ev.u.input.data, report, target_len > 128 ? 128 : target_len);

        ssize_t ret = write(uhid_fd, &ev, sizeof(ev));
        if (ret < 0) {
            fprintf(stderr, "[uinput_proto] Failed to write UHID_INPUT: %s (len=%d, errno=%d)\n",
                    strerror(errno), len, errno);
        }
        return;
    }

    if (strstr(line, "\"command\":\"get_report_reply\"") || strstr(line, "\"command\": \"get_report_reply\"")) {
        int req_id = 0;
        char *p_id = strstr(line, "\"id\"");
        if (p_id) {
            p_id = strchr(p_id, ':');
            if (p_id) req_id = (int)strtol(p_id + 1, NULL, 10);
        }
        int err = 0;
        char *p_err = strstr(line, "\"err\"");
        if (p_err) {
            p_err = strchr(p_err, ':');
            if (p_err) err = (int)strtol(p_err + 1, NULL, 10);
        }
        uint8_t report[1024];
        memset(report, 0, sizeof(report));
        int len = parse_int_array(line, "\"report\"", report, sizeof(report));

        struct uhid_event resp;
        memset(&resp, 0, sizeof(resp));
        resp.type = UHID_GET_REPORT_REPLY;
        resp.u.get_report_reply.id = req_id;
        resp.u.get_report_reply.err = err;
        resp.u.get_report_reply.size = len;
        memcpy(resp.u.get_report_reply.data, report, len > 1024 ? 1024 : len);

        write(uhid_fd, &resp, sizeof(resp));
        return;
    }

    if (strstr(line, "\"command\":\"set_report_reply\"") || strstr(line, "\"command\": \"set_report_reply\"")) {
        int req_id = 0;
        char *p_id = strstr(line, "\"id\"");
        if (p_id) {
            p_id = strchr(p_id, ':');
            if (p_id) req_id = (int)strtol(p_id + 1, NULL, 10);
        }
        int err = 0;
        char *p_err = strstr(line, "\"err\"");
        if (p_err) {
            p_err = strchr(p_err, ':');
            if (p_err) err = (int)strtol(p_err + 1, NULL, 10);
        }

        struct uhid_event resp;
        memset(&resp, 0, sizeof(resp));
        resp.type = UHID_SET_REPORT_REPLY;
        resp.u.set_report_reply.id = req_id;
        resp.u.set_report_reply.err = err;

        write(uhid_fd, &resp, sizeof(resp));
        return;
    }
}

int main(void) {
    int err_fd = open("/data/local/tmp/uhid_err.log", O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (err_fd >= 0) {
        dup2(err_fd, 2);
        close(err_fd);
    }

    signal(SIGINT,  on_signal);
    signal(SIGTERM, on_signal);

    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stdin, NULL, _IOLBF, 1024);

    struct pollfd pfds[2];
    pfds[0].fd = 0; // stdin
    pfds[0].events = POLLIN;

    pfds[1].fd = -1;
    pfds[1].events = POLLIN;

    char stdin_buf[16384];
    int stdin_len = 0;

    fprintf(stderr, "[uinput_proto] UHID companion daemon started, waiting for registration...\n");

    while (running) {
        int active_fds = (uhid_fd >= 0) ? 2 : 1;
        pfds[1].fd = uhid_fd;

        int ret = poll(pfds, active_fds, -1);
        if (ret < 0) {
            if (errno == EINTR) continue;
            break;
        }

        if (pfds[0].revents & POLLIN) {
            char chunk[4096];
            ssize_t r = read(0, chunk, sizeof(chunk) - 1);
            if (r <= 0) {
                fprintf(stderr, "[uinput_proto] stdin EOF, exiting.\n");
                break;
            }
            chunk[r] = '\0';

            for (int i = 0; i < r; i++) {
                if (chunk[i] == '\n' || stdin_len >= (int)sizeof(stdin_buf) - 1) {
                    stdin_buf[stdin_len] = '\0';
                    handle_stdin_command(stdin_buf);
                    stdin_len = 0;
                } else {
                    stdin_buf[stdin_len++] = chunk[i];
                }
            }
        }

        if (uhid_fd >= 0 && (pfds[1].revents & POLLIN)) {
            struct uhid_event ev;
            // Drain all pending events non-blockingly
            while (read(uhid_fd, &ev, sizeof(ev)) == (ssize_t)sizeof(ev)) {
                if (ev.type == UHID_OUTPUT) {
                    int size = ev.u.output.size;
                    // Pad output reports to a minimum size of 16 to pass array checks in app process
                    int print_size = (size < 16) ? 16 : size;
                    if (size > 0 && size <= 1024) {
                        printf("{\"event\":\"output\",\"id\":1,\"reportData\":[");
                        for (int i = 0; i < print_size; i++) {
                            int val = (i < size) ? ev.u.output.data[i] : 0;
                            printf("%d", val);
                            if (i < print_size - 1) printf(",");
                        }
                        printf("]}\n");
                        fflush(stdout);
                    }
                }
                else if (ev.type == UHID_GET_REPORT) {
                    // Forward GET_REPORT to the Java app
                    printf("{\"event\":\"get_report\",\"id\":%u,\"reportId\":%u,\"reportType\":%u}\n",
                           ev.u.get_report.id, ev.u.get_report.rnum, ev.u.get_report.rtype);
                    fflush(stdout);
                }
                else if (ev.type == UHID_SET_REPORT) {
                    // Forward SET_REPORT to the Java app
                    printf("{\"event\":\"set_report\",\"id\":%u,\"reportId\":%u,\"reportType\":%u,\"reportData\":[",
                           ev.u.set_report.id, ev.u.set_report.rnum, ev.u.set_report.rtype);
                    int size = ev.u.set_report.size;
                    for (int i = 0; i < size; i++) {
                        printf("%d", ev.u.set_report.data[i]);
                        if (i < size - 1) printf(",");
                    }
                    printf("]}\n");
                    fflush(stdout);
                }
            }
        }
    }

    if (uhid_fd >= 0) {
        struct uhid_event ev;
        memset(&ev, 0, sizeof(ev));
        ev.type = UHID_DESTROY;
        write(uhid_fd, &ev, sizeof(ev));
        close(uhid_fd);
        fprintf(stderr, "[uinput_proto] UHID Device destroyed, fd closed.\n");
    }

    return 0;
}
