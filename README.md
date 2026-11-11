# Together Tonight

Private couples activities app for Mahesh & Ekashmi — just the two of them, on his Android phone, offline, no login, no account.

Single native Activity hosting a WebView; the whole app is bundled as `app/src/main/assets/index.html` (no external dependencies, no CDN). Internet is used ONLY to fetch `data.json` from GitHub for content sync (v1.4) — no analytics, no account, nothing else ever uses the network.

Package: com.mahesh.togethertonight — versionCode 5, versionName 1.4, minSdk 24, targetSdk 34, compileSdk 34.

## v1.4 — Extra Spicy, properly filthy + Heat filter

**Extra Spicy, for real this time:** the Extra Spicy questions deck was rewritten from scratch — 112 filthy, explicit, original questions (dirty talk in graphic words, oral, anal play and pegging, toys, bondage, spanking, domination/submission, roleplay, public/risky teasing, voyeur/exhibitionist fantasies, cum play, squirting, edging and denial, multiple orgasms, threesome fantasies, body worship, rough vs slow, blindfolded, tied up, choking play with an explicit safety note). No euphemisms — the words couples actually use in bed. Question counts are now Fun 112, Deep 109, Spicy 105, Extra Spicy 112 (438 total). Extra Spicy stays PIN-gated with the same 4-digit After Dark PIN.

**Extra Spicy activities + Heat filter:** After Dark grew from 40 to 66 ideas. Every dark idea now carries a `heat` tag — Spicy 29, 🔥 Extra Spicy 37 — with filter chips (All / Spicy / 🔥 Extra Spicy, with counts) alongside the For Her / For Him / For Both chips. 26 new Extra Spicy activities were added (dirty-talk-only night, teacher/boss/stranger/maid/cop roleplay, blindfold + ice + hot wax, spanking session, tied to the bedposts, oral marathon / 69, anal slow and proper, pegging night, edging until begging, toy nights, public teasing, sexting all day, cum play, make-her-squirt, don't-stop-after-the-first-one, voyeur fantasy, choking play with a safety note, threesome fantasy with just the two of you). Each has an explicit 2–3 sentence description, what you need, and a tip — plus safety notes on choking, wax, bondage and anal (limits + safe word first, go slow, stop the second either of you says so). Manage Ideas now has a Heat dropdown for After Dark activities, and Export/Import carry the heat field. Surprise Me still excludes all After Dark, and Bluetooth suggestions for extra items stay generic + PIN-gated.

**Bug fix — content could not load (root cause):** the app loaded its list only via `fetch('data.json')`, which commonly fails on `file:///android_asset` in WebView even with file-access settings on — and the fallback was only 3 activities + 2 questions, which is exactly the near-empty screen Mahesh reported. Fixed in v1.4: the FULL list is now also embedded inside `index.html` (`<script type="application/json" id="embedded-data">`, generated from `data.json` at build time). The app tries `data.json` first, then the complete embedded copy, and only then the tiny stub — and if it had to fall back it shows a banner ("Content did not load from its file — showing the built-in list") instead of a blank screen. Verified by simulating a fetch failure: the deck still renders 116 activities, 438 questions and 120 dares.

**Truth or Dare + Dares deck (new in v1.4):** there were questions but no dares. Now there are 120 dares in `data.json` — Fun 40, Spicy 40, 🔥 Extra Spicy 40, all original concrete actions to do (Fun: Bollywood serenade, blindfolded dessert; Spicy: 60-second hands-free kiss, slow massage; Extra Spicy: filthy dares in the same tone as the Extra Spicy questions — dirty-talk, strip tease, bondage, oral, roleplay, toys, edging — PIN-gated). The Questions tab is now **Truth or Dare**: a Truth / Dare / Both toggle, dares use Fun / Spicy / Extra Spicy filters (no Deep dares), the same filtered swipe left/right + Next/Prev + counter ("Dare 7 of 40 · Spicy"), Shuffle, and Send to Partner. Extra Spicy dares are PIN-gated exactly like Extra Spicy questions. The Home screen has a clear "🎲 Truth or Dare" card showing the live counts ("438 questions · 120 dares · 116 ideas"), and the bottom-nav Questions tab opens the deck immediately. Manage Ideas can add/edit dares too, and Export/Import carry them.

**GitHub sync (new in v1.4):** the app syncs its content from GitHub — `https://raw.githubusercontent.com/mahashg/together-tonight/main/app/src/main/assets/data.json` — on every launch (in the background) and whenever you tap **Sync from GitHub** in Manage Ideas. How to update content with no app update: edit `app/src/main/assets/data.json` on GitHub `main` and both phones pick it up on next launch or via the Sync button. The sync compares the `version` field first, but if the version is equal and the counts differ it still takes the remote copy (an edit without a version bump). The synced copy is cached on the phone, so a later offline launch still uses the last synced list. If GitHub is unreachable (offline, 404, bad JSON) the app silently keeps its built-in list and shows "Offline — built-in list" in the footer — never an error popup, never a blank screen. A sync never touches your added ideas, hidden items, Favourites, Done history, PIN or Bluetooth link. The Sync button shows Last synced, Source (GitHub / Built-in) and the live counts. Permissions added for this: `INTERNET` + `ACCESS_NETWORK_STATE`, used only for this fetch.

## v1.3 — Spicy split + For Her / For Him + fixed Questions deck

**Spicy split:** the Questions deck now has four kinds — Fun 112, Deep 109, Spicy 105, Extra Spicy 105 (431 total, all original). The old 110 Spicy questions were reclassified: milder, flirty, romantic ones stay Spicy; more explicit, kinkier, fantasy-heavy ones moved to Extra Spicy (re-id'd extra-001…), and new questions were written to bring each to 100+. **Extra Spicy is PIN-gated** with the same 4-digit After Dark PIN — tapping the Extra Spicy filter, or opening an Extra Spicy question sent by your partner, shows the PIN screen first. Spicy stays open as before.

**After Dark filters:** After Dark grew from 14 to 40 ideas. Every dark idea carries a `for` tag — For Her 15, For Him 13, For Both 12 — with filter chips (All / For Her / For Him / For Both, with counts) and a search box inside After Dark. For Her ideas focus on what many women say they enjoy most (slow build-up, being asked what feels good, focused attention, praise, aftercare); For Him ideas focus on what many men say they enjoy most (enthusiasm and initiation, feeling desired, oral, dress-up, variety, praise). Every person is different — the section says so plainly: these are starting points, talk about what you each actually like. Manage Ideas now has a For dropdown (shown when Category is After Dark) and an Extra Spicy kind option; Export/Import carry both fields. The After Dark PIN gate is unchanged, and Surprise Me still excludes After Dark entirely.

**Questions deck fixed (v1.3 bug fix):** the deck had no swipe handling at all, no counter, and reshuffled instead of wrapping at the end. Now each filter tap rebuilds the deck from that kind only, resets to question 1, and shows "Question N of M · Fun". Swipe left = next, swipe right = previous (predominantly horizontal, >50px, vertical scrolling untouched), wrapping at both ends, plus explicit Previous / Next buttons and a "Swipe left / right, or tap Next" hint. Verified in node against the real app code: 39/39 checks — every filter (All/Fun/Deep/Spicy/Extra Spicy) steps Next 5× and swipes left 5× with distinct in-kind questions, counter updates, wrap works both ways, and the Extra Spicy PIN gate opens first.

## v1.2 — After Dark PIN + content in data.json

**After Dark PIN (set once, 4 digits):** the first time you tap After Dark, you set a 4-digit PIN on an on-screen keypad (digits never shown, only dots; no system keyboard). After that, the PIN is required every time you enter After Dark — including from a partner's notification tap or a search result. Leaving After Dark, locking it, or the app going to the background locks it again. Only the SHA-256 hash (PIN + app salt) is stored in native SharedPreferences — the PIN itself is never stored or logged. 3 wrong tries in a row locks the PIN screen for 30 seconds. **Change PIN** and **Lock now** are inside After Dark. **Forgot PIN?** There is no reset code and no backdoor: clear the app's storage in Android Settings > Apps > Together Tonight > Storage > Clear data. That also clears Favourites, Done history and the Bluetooth link, by design.

**Content lives in data.json:** all 64 activities and 331 Couples Questions (Fun 112, Deep 109, Spicy 110 — 100+ in every category, all written for this app) now live in `app/src/main/assets/data.json` (`{"version","activities":[...],"questions":[...]}`), loaded by `index.html` at startup with a tiny 3-item fallback only if the file cannot load. To update content without touching code, edit `data.json` and rebuild — or add ideas in the app under **Manage Ideas**, no rebuild needed. Manage Ideas lets you add your own activities/questions, hide or show any item (built-ins can be hidden, not deleted), edit/delete your own, Reset to the original list, Export the list as JSON, and Import JSON by pasting or picking a `.json` file.

## v1.1 — Bluetooth partner link

Install the same APK on both phones. No internet, no account — Bluetooth Classic (RFCOMM) only, fixed app UUID, one-line JSON messages.

How to link (once):
1. Both phones: Bluetooth on, open the app, tap **Link** in the bottom nav.
2. One phone taps **Make Me Discoverable** (5 minutes); the other taps **Find Partner's Phone** and taps its name in the list.
3. Android asks to pair — accept on both phones. The apps then exchange a hello with a 4-digit code; compare the code out loud. Both phones store the link.

After that: tap **Send to Partner ♥** on any activity (or on a question card) when you are nearby. Your partner gets a notification and, if the app is open, an in-app banner that opens the idea.

Discreet notification: the notification is always generic — title "Together Tonight", text "You have a new idea waiting — open to see" — and `VISIBILITY_SECRET`, so nothing about the idea ever shows on the lock screen. After Dark ideas also show a generic banner until tapped.

Limits: works in the same room/house (Bluetooth range, ~10 m), Bluetooth on, app opened recently. If a phone has been locked a long time, Android may pause listening — open the app once to reconnect. The linked foreground service (`LinkService`, `connectedDevice` type) keeps listening only while linked; **Unlink** on the Link screen clears the stored partner and stops it.

Debug signing is pinned to ~/workspace/.tooling/signing/debug.keystore (expected SHA-1 0A:1A:71:63:12:09:A8:1A:7B:69:F7:F0:C2:B6:2A:53:4A:A8:46:CE).

Build offline:
```
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"
export JAVA_HOME=~/workspace/.tooling/jdk17
export PATH=$JAVA_HOME/bin:$PATH
export GRADLE_USER_HOME=~/workspace/.gradle-hindu
~/workspace/.tooling/gradle-8.9/bin/gradle :app:assembleDebug --offline
```
