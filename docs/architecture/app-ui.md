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

- **Circles** use the `tertiary` role (`tertiaryContainer` for their icon).
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
| `MemberRow`, `CircleRow` | List rows for members and circles: a name with an optional suffix ("You"), a status line after the freshness dot, an optional trailing slot |
| `FormField` | Single-line outlined field; `password` adds the eye toggle, `monospace` is for codes |
| `DayChips` | One filter chip per day of the 7 days of history |
| `InfoCard` | Tonal box with an icon, for a note about what a screen does |
| `PeekHeaderBottomSheetScaffold` | The map sheet: collapsed to its header, never hidden |
| `ServerUnreachableBanner` | The pill under the top bar while the server can't be reached |

## The map

The map is the only starting point of the app, and a single route: what changes is the bottom
sheet, never hidden, whose content comes from `SheetContent` (`ui/screens/map/SheetContent.kt`),
worked out from the groups and the group on screen:

| Content | Collapsed (header) | Expanded (body) |
|---|---|---|
| `NoGroups` | "Start a group": create, or join with a code | — |
| `GroupGone` | The group was deleted or this phone's member removed; remove it from this phone | — |
| `Alone` | Sharing row, then "You're the only one here" with an invite button | — |
| `Group` | Sharing row only: the members are the markers on the map | Members, then the circles with someone inside and one row for all the others |
| `Member` | Avatar, name, status; Follow and Maps | Notifications about them, last update, role, member ID |
| `Following` | The followed member only; a pill under the top bar stops following | — |

- **Tapping a member doesn't follow them.** The camera moves to them once and the sheet shows
  their details; following is the Follow button, and a drag of the map or ✕ on the pill stops it.
- **A member's status** is one line built by `memberStatus`: the circle they are in, how they move
  and how long ago ("At Home · still · just now"). It doesn't say how far they are from this
  phone: the map already shows it. A position older than
  the sharing window ([Sharing](sharing.md)) reads "Paused sharing" and greys the avatar.
- **Notifications about a member** are switched off per member on this phone only, in the same
  Room table as the muted circles ([Circles](circles.md)).
- **Times** read as people say them: "Today at 13:25", "Joined yesterday", "3 Nov at 18:40".
- **Can't connect**: while the server can't be reached, a pill under the top bar says positions
  may be old, with Retry, and the sharing row adds "Sends again once connected".
- Tapping a circle with someone in it fits it in the map and draws its edge.
- **Members are balloons**, like Life360: their avatar in a white balloon whose tip is the exact
  position, the name under the tip, a halo in their colour when selected. This phone's own
  position stays a dot, so it never reads as someone else.
- **Layers**, from the bottom: the edge of the circle picked, the circle pins, this phone's dot,
  the members. A balloon over a pin hides it rather than the other way round, since the people
  matter more than the places.
- The ⋮ menu has Invite people, Circles, Leave group and, for admins, Delete group; a group this
  phone can no longer use only offers to remove it.

## Circles

The Circles page lists every circle of the group by name, with a local search once there is one.
Each row says the radius and who is inside, with their avatars, and ends with a bell: on, the
phone is notified when someone arrives or leaves; off, it isn't, on this phone only. The ⋮ menu
turns every bell on or off. Tapping a circle opens the editor, which is also where it is deleted.

On the map a circle is a pin with its name, never a drawn area: with dozens of circles, or large
ones, drawn areas would cover the map and the members, and a 30 m circle seen from afar would
vanish. Only the circle tapped (on its pin or its row in the sheet) or being edited shows its
edge. The pins are the scheme's `outline` and the circle picked is `tertiary`, so neither looks
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
