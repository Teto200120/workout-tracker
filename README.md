# Hector's Workout Tracker

A local-first progressive web app for tracking workouts, routines, progress, and backups.

## Development

Prerequisites: Node.js 24 and npm.

```text
npm install
npm run serve
```

The local app is served at `http://127.0.0.1:4175`. The deployed application remains plain HTML, CSS, and JavaScript; Node dependencies are development tools only.

### Test on your local network

To open the app on a phone connected to the same Wi-Fi network, run:

```text
npm run serve:lan
ipconfig
```

Find the PC's Wi-Fi IPv4 address in `ipconfig`, then open `http://YOUR-PC-IP:4175` on the phone. If the phone cannot connect, allow Node.js through Windows Firewall on private networks.

This LAN server is for browser testing only. Service workers and installed-PWA testing may not work over plain LAN HTTP; use the normal HTTPS deployment for those checks.

Use these checks while working:

```text
npm run lint
npm run format:check
npm run test
npm run test:e2e
npm run test:e2e:ui
npm run check
```

`npm run check` runs the same core lint, formatting, and browser-test validation used by GitHub Actions. See [docs/TESTING.md](docs/TESTING.md) for test scope and debugging guidance.
