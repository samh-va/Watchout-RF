import usb.core
import usb.util
import sys

dev = usb.core.find(idVendor=0x0bda, idProduct=0x2838)
if dev is None:
    print("Device not found")
    sys.exit(1)

dev.set_configuration()

# Enable I2C repeater
try:
    dev.ctrl_transfer(0x40, 0, 0x0120, 0x0011, [0x18], 300)
    print("I2C repeater write: SUCCESS")
except Exception as e:
    print(f"I2C repeater write: FAILED ({e})")

# I2C probe write
try:
    dev.ctrl_transfer(0x40, 0, 0x34, 0x0610, [0x00], 300)
    print("I2C probe write: SUCCESS")
except Exception as e:
    print(f"I2C probe write: FAILED ({e})")

