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
- **A member's status** is one line built by `memberStatus`: the circle they are in, how they move,
  how far from this phone and how long ago ("At Home · still · just now"). A position older than
  the sharing window ([Sharing](sharing.md)) reads "Paused sharing" and greys the avatar.
- **Notifications about a member** are switched off per member on this phone only, in the same
  Room table as the muted circles ([Circles](circles.md)).
- **Times** read as people say them: "Today at 13:25", "Joined yesterday", "3 Nov at 18:40".
- **Can't connect**: while the server can't be reached, a pill under the top bar says positions
  may be old, with Retry, and the sharing row adds "Sends again once connected".
- The ⋮ menu has Invite people, Circles, Leave group and, for admins, Delete group; a group this
  phone can no longer use only offers to remove it.
