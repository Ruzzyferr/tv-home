# Changelog

APKs for every version are on the [Releases](https://github.com/Ruzzyferr/tv-home/releases) page.

## 1.2.0 · 2026-10-22

### New
- **Opening animation** when the TV starts or wakes up. The Swiip wordmark rises in, two gold stars fall onto the dotless ı's, lift into eyes, and a grin draws itself underneath. It lasts about 8 seconds and any key skips it. It is drawn from vector outlines and renders at about 12 ms per frame on the test TV.

### Fixed
- **Holding OK on an IR remote no longer clicks the first menu entry.** An IR signal can drop for a moment while a button is held, so the long-press menu read the hold as a new press and added or removed the app from the Dock by itself. Menus now ignore OK until it has been quiet for 350 ms.

## 1.1.0 · 2026-10-22

### New
- **English and Turkish interface.** It follows the TV's language, and *Control Center → English / Türkçe* switches TV Home alone (Android 13+).
- The phone touchpad page follows the phone's language.

### Changed
- Control Center fits on a 1080p screen with the new language row.
- Demo GIF and English screenshots in the README.

## 1.0.0 · 2026-10-22

First public release.

- macOS-style Dock with spring magnification. You can pin apps and HDMI-CEC devices (a PS5 shows its brand badge).
- "Continue watching" from media sessions: the last YouTube video or episode with artwork, in-video frames and one-press resume.
- Launchpad with automatic categories, Spotlight search, and Control Center (volume, RAM, Free RAM, simple and detailed views).
- Phone as mouse and keyboard through a PIN-protected web touchpad, with no app needed.
- Toolkit: reversible debloat, RAM cap, AOT compile, launcher takeover, sideloading from official GitHub releases, and scrcpy mirroring.
