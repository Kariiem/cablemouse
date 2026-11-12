Cable Mouse helper
==================

Run this on the PC that the phone is plugged into. It receives the phone's
touchpad movements over the USB cable and moves the PC's mouse cursor.
Nothing is installed and no administrator rights are needed.

Files
-----
  cablemouse-legacy.exe   Windows 10/11. (Windows 7/8.1 also need the
                          "Universal C Runtime" update, KB2999226.)

How to use
----------
1. Copy cablemouse-legacy.exe from the phone to the PC (any folder).
2. On the phone, set the USB mode to MIDI: tap the USB notification and
   choose MIDI (or Settings > Connected devices > USB).
3. Double-click cablemouse-legacy.exe. It prints "connected" once the
   Cable Mouse app is open on the phone.
4. Use the touchpad in the Cable Mouse app.

Notes
-----
- Windows SmartScreen or antivirus software may warn about the program,
  because it is unsigned and it sends mouse input. Choose "More info" >
  "Run anyway", or ask your administrator.
- Close the window to quit. If the phone is unplugged and plugged in again,
  the helper reconnects by itself.
