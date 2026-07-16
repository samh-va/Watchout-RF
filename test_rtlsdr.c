#include <stdio.h>
#include <rtl-sdr.h>

int main() {
    rtlsdr_dev_t *dev = NULL;
    int r = rtlsdr_open(&dev, 0);
    if (r < 0) {
        printf("Failed to open rtlsdr\n");
        return 1;
    }
    printf("Successfully opened rtlsdr\n");
    rtlsdr_close(dev);
    return 0;
}
