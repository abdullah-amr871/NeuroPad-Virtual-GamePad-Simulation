/* Host-side unit test for json_mini.h:  cc -o test_json test_json.c && ./test_json */
#include <assert.h>
#include "json_mini.h"

int main(void) {
    const char *reg = "{\"id\":1,\"command\":\"register\",\"name\":\"Xbox Wireless Controller\","
                      "\"vid\":1118,\"pid\":765,\"bus\":\"bluetooth\",\"descriptor\":[5,1,9,5,161,1,0xC0],"
                      "\"uniq\":\"aa:bb\",\"feature_reports\":[{\"id\":5,\"data\":[5,30]}]}";
    char text[64];
    uint8_t bytes[32];

    assert(get_string(reg, "command", text, sizeof(text)) && strcmp(text, "register") == 0);
    assert(get_string(reg, "name", text, sizeof(text)) && strcmp(text, "Xbox Wireless Controller") == 0);
    assert(get_string(reg, "bus", text, sizeof(text)) && strcmp(text, "bluetooth") == 0);
    assert(get_int(reg, "vid", 0) == 1118);
    assert(get_int(reg, "pid", 0) == 765);
    assert(get_int(reg, "id", 0) == 1); /* the first "id", not the one inside feature_reports */
    assert(get_int_array(reg, "descriptor", bytes, 32) == 7 && bytes[4] == 161 && bytes[6] == 0xC0);
    assert(get_int(reg, "missing", -7) == -7);

    /* "report" appears first as the *value* of "command" and must be skipped there. */
    const char *rep = "{\"id\":1,\"command\":\"report\",\"report\":[1,128,127,255,0]}";
    assert(get_int_array(rep, "report", bytes, 32) == 5 && bytes[3] == 255 && bytes[4] == 0);
    assert(get_int_array(rep, "report", bytes, 3) == 3); /* respects the output limit */

    /* Both spellings of the error field. */
    const char *reply = "{\"command\":\"get_report_reply\",\"id\":42,\"err\":1,\"error\":1}";
    assert(get_int(reply, "id", 0) == 42);
    assert(get_int(reply, "err", get_int(reply, "error", 0)) == 1);
    const char *legacy = "{\"command\":\"get_report_reply\",\"id\":7,\"error\":1,\"report\":[]}";
    assert(get_int(legacy, "err", get_int(legacy, "error", 0)) == 1);
    assert(get_int_array(legacy, "report", bytes, 32) == 0);

    /* Spaces after the colon, garbage inside an array, and truncated strings. */
    const char *spaced = "{\"command\": \"report\", \"report\": [1, 2,x3]}";
    assert(get_string(spaced, "command", text, sizeof(text)) && strcmp(text, "report") == 0);
    assert(get_int_array(spaced, "report", bytes, 32) == 2);
    assert(get_string(reg, "name", text, 5) && strcmp(text, "Xbox") == 0);
    assert(!get_string("{\"name\":\"unterminated", "name", text, sizeof(text)));

    puts("json_mini: all tests passed");
    return 0;
}
