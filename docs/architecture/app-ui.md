# App UI

[← Architecture](../architecture.md)

The Android app follows Material 3. This page records the theme and the shared building blocks
the screens are made of.

## Theme

`ui/theme/`: one orange colour scheme, light and dark following the system. The primary is
`#8B5000` (container `#FFDCBE`), the dark primary `#FFB870`.

- **No dynamic colour.** The app's minimum is Android 12, where dynamic colour is always
  available, so keeping it on would mean nobody ever sees the orange scheme. The orange scheme is
  the app's identity on every phone.
- **Typography and shapes are Material's own.** The default `Typography()` and shapes already are
  the M3 scale (Roboto on Android; 28 dp corners for sheets and the floating bar, 16 dp for FABs
  and cards), so the app doesn't redeclare them.

### Colours with a meaning

Every colour with a meaning comes from the scheme, so it follows the accent:

- **Places** use the `tertiary` role (`tertiaryContainer` for their icon).
- **Members** get one of five colours made from the primary's hue turned by 60° steps, with saturation and lightness fixed per theme so a filled avatar always reads
  with a white (light) or dark (dark theme) initial. The colour is chosen from the member id, so
  every phone shows the same one. This phone's member is the primary itself.
- **Activities** follow the accent too: still is the scheme's `outline`, the neutral case; on
  foot, by bike and in a vehicle are the primary's hue turned by 180°, 90° and 240°, more vivid
  than the members so a route stands out on the map. With the orange primary they are blue,
  green and violet. They paint the history routes and the activity badge on an avatar.

`LockateColors`, read with `LockateTheme.colors`, holds the activities and the member colours,
built from the scheme by `lockateColors`.

The freshness dot (green, yellow, grey) is a separate thing, described in [Sharing](sharing.md).

## Shared components

`ui/composables/`, each with a light and a dark `@Preview` (`@ThemePreviews`):

| Component | What |
|---|---|
| `FloatingTopBar` | The 56 dp pill over the map: navigation, title and subtitle, actions |
| `SharingRow` | "Sharing your location" with a switch and an optional second line; the whole row toggles |
| `MemberAvatar` | Initials on the member's colour, activity badge, greyed when paused |
| `MemberRow`, `PlaceRow` | List rows for members and places: a name with an optional suffix ("You"), a status line after the freshness dot, an optional trailing slot |
| `FormField` | Single-line outlined field; `password` adds the eye toggle, `monospace` is for codes |
| `DayChips` | One filter chip per day of the 7 days of history |
| `InfoCard` | Tonal box with an icon, for a note about what a screen does |
| `CollapsibleSheetScaffold` | The sheets of the map and the place editor: collapsed, only the drag handle shows |
| `ServerUnreachableBanner` | The pill under the top bar while the server can't be reached |

## The map

The map is the only starting point of the app, and a single route: what changes is the bottom
sheet, whose content comes from `SheetContent` (`ui/screens/map/SheetContent.kt`), worked out
from the groups and the group on screen:

| Content | The sheet shows |
|---|---|
| `NoGroups` | "Start a group": create, or join with a code |
| `GroupGone` | The group was deleted or this phone's member removed; remove it from this phone |
| `Alone` | Sharing row, then "You're the only one here" with an invite button |
| `Group` | Sharing row, then "Members · 3" and one row per member |
| `Member` | Avatar, name, status; Follow and Maps; notifications about them, last update, role, member ID |
| `Following` | Nothing: the sheet goes down and a pill under the top bar says who, and stops following |

- **The sheet collapses all the way**: down, only its drag handle shows and the map is free; the
  sharing row is not kept in view. A new content (another member, back to the group) opens the
  sheet on it, and following a member closes it. The my-location button shows only with the
  sheet down.
- **The top bar** has the group's name in the middle and nothing under it, except a red
  "Removed" when this phone is no longer in the group. How many members there are is in the
  sheet, next to "Members".
- **Places are not in the sheet**: each member's status already says the place they are in, and
  the places have their own page in the ⋮ menu.

- **Tapping a member doesn't follow them.** The camera moves to them once and the sheet shows
  their details; following is the Follow button, and a drag of the map or ✕ on the pill stops it.
- **A member's status** is one line built by `memberStatus`: the place they are in, how they move
  and how long ago ("At Home · still · just now"). It doesn't say how far they are from this
  phone: the map already shows it. A position older than
  the sharing window ([Sharing](sharing.md)) reads "Paused sharing" and greys the avatar.
- **Notifications about a member** are switched off per member on this phone only, in the same
  Room table as the muted places ([Places](places.md)). One switch covers both what is notified
  about them: arriving at or leaving a place, and starting or stopping sharing
  ([Sharing](sharing.md)). It reads "Places and sharing", not "online": the server only knows
  when positions arrive, so "stopped sharing" is all it can tell.
- **Times** read as people say them: "Today at 13:25", "Joined yesterday", "3 Nov at 18:40".
- **Can't connect**: while the server can't be reached, a pill under the top bar says positions
  may be old, with Retry, and the sharing row adds "Sends again once connected".
- **Members are balloons**, like Life360: their avatar in a white balloon whose tip is the exact
  position, the name under the tip, a halo in their colour when selected. This phone's own
  position stays a dot, so it never reads as someone else.
- **Layers**, from the bottom: the edge of the place picked, the place pins, this phone's dot,
  the members. A balloon over a pin hides it rather than the other way round, since the people
  matter more than the places.
- The ⋮ menu has Invite people, Places, Leave group and, for admins, Delete group; a group this
  phone can no longer use only offers to remove it.

## Places

The Places page ("Family's places") lists every place of the group by name, with a local search once there is one.
Each row says the radius and who is inside, with their avatars, and ends with a bell: on, the
phone is notified when someone arrives or leaves; off, it isn't, on this phone only. The ⋮ menu
turns every bell on or off. Tapping a place opens the editor, which is also where it is deleted.

On the map a place is a pin with its name, never a drawn area: with dozens of places, or large
ones, drawn areas would cover the map and the members, and a 30 m place seen from afar would
vanish. Only the place tapped (on its pin) or being edited shows its
edge. The pins are the scheme's `outline` and the place picked is `tertiary`, so neither looks
like this phone's position, which is the primary.

## Servers

A server is a context: the drawer lists the servers, each with its address and how many groups
this phone has there, and switching shows only that server's groups. Nothing else in the app
names the server. The pencil on a row opens the server screen, which has three modes
(`ConnectionSettingsRoute.mode`):

| Mode | When | What |
|---|---|---|
| `FirstLaunch` | No server yet | Address and API key; the name is the host |
| `Add` | Add server, from the drawer | Address, a name that follows the host until edited, API key |
| `Edit` | The pencil | Name, address, API key, the groups on it, its version, and Remove from this phone |

- **One server per address.** `ServerUrl.normalize` is the one form an address is saved in
  (scheme and host in lower case, no default port, no trailing slash), and Room keeps it unique.
  Adding an address already saved opens that server with "Already added". The database
  migration to version 8 merged the servers saved twice into the oldest, with their groups and
  the newest API key.
- **Removing a server** leaves its groups (or drops them, if the server can't be reached) after
  a dialog that names them.
- There is no "unreachable" mark in the drawer: the "Can't connect" pill on the map says it for
  the server in use.

## Groups

- **The group picker** opens from the top bar: search, New group and Join, and one row per group
  of the server in use, with whether this phone shares and how many members it has ("Sharing · 3
  members", "Not sharing · only you"), or why it can't be used anymore.
- **New group** asks for the group's name and this member's name; the map then opens on the
  group, alone in it.
- **Join a group**: Scan QR code, or the code typed in a monospace field ([Invites](invites.md)).
  Joining is a full screen with its two steps and Cancel, not a dialog. Once in, the map opens
  on the group, on whichever server it is.
- **Invite**: the code on one line, as it is typed (`42-fog-dimly-old`, shrunk to fit), so the
  number reads as part of it; Copy and Share, the
  QR code (`QrCode`, always black on white, which scanners read best), a bar running down the
  10 minutes, and the reminder that the first one who enters it gets in.

## Permissions

- **One screen, not a queue of dialogs**: a card for each permission Lockate asks for, in order
  (`LockatePermission`): precise location, location all the time, notifications (only from
  Android 13, which is when it needs asking) and physical activity. Each says what it is used
  for; the next one still off is outlined and has the filled button.
- **Sharing needs the two location permissions**; notifications and physical activity are up to
  the user. Without physical activity the GPS stays on while still and members show no activity
  ([Sending](sending.md)).
- **Location all the time can't be asked with a dialog** since Android 11: its button opens the
  system page where the user picks "Allow all the time". It appears only once precise location
  is on, because Android grants it only after that. Android also stops showing a permission's
  dialog after two refusals, so a second tap on a refused permission opens the app's page in
  the system settings.
- **When it opens**: turning sharing on while a location permission is missing, and once per
  launch when the group on the map shares but the permissions are missing (a group just created
  or joined on a new phone), and from "Center on me" while they are missing (the map's own
  position uses the same check as sending, so it needs both too). It reads the permissions again every time it comes back to the
  front; as soon as both location permissions are on it turns sharing on for that group and goes
  back to the map. "Not now" goes back without changing anything. From Settings → Permissions it
  only shows and asks.
- While the group shares and a location permission is missing, the sharing row on the map says
  "Waiting for location permission".
- **The sending service starts only with the location permission**: Android kills an app whose
  foreground service doesn't go to the foreground in time, and a location service can't without
  it. The map starts it again when the permissions come on.

## Settings

- **Map**: light style, dark style and place search, one row each with the service in use
  ("OpenFreeMap Liberty · default", or the address without `https://`). A row opens a dialog
  with the URL; Save first checks it answers like the service it replaces ("Checking…"), and an
  empty field goes back to the default. A card reminds that tiles and searches go straight from
  the phone to these services, which can tell which area the user looks at.
- **This phone**: Permissions, with a summary ("Location all the time · notifications on").
- **Reset map and search to defaults** at the bottom forgets the three URLs.
