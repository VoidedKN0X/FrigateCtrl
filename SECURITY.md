# Security policy

Thank you for helping keep FrigateCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/FrigateCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of FrigateCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

- Your Frigate password, login token or camera footage leaking to anyone other than your own Frigate server
- Ways around the app lock, such as camera pictures or video showing while FrigateCtrl is locked, or protected screens showing up in screenshots when they shouldn't
- Another app on the phone being able to make FrigateCtrl do something you didn't ask for (for example through its notifications, push receiver or shared clips)
- Accepting a server certificate you didn't confirm, or sending your login over plain http without your confirmation
- Anything that lets someone run code or read files they shouldn't

Problems in Frigate NVR or go2rtc themselves should go to those projects.

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
C4:C9:D4:4D:BE:E9:FA:67:EB:11:B3:27:E9:16:B3:A7:17:DE:96:CB:41:F7:E2:FC:EF:39:AC:24:38:9D:B8:CA
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
