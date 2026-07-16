import urllib.request
import ssl
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

url = "https://raw.githubusercontent.com/osmocom/rtl-sdr/master/src/librtlsdr.c"
try:
    response = urllib.request.urlopen(url, context=ctx)
    text = response.read().decode('utf-8')
    for line in text.splitlines():
        if "R820T_I2C_ADDR" in line:
            print(line)
except Exception as e:
    print(f"Error: {e}")
