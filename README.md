# Utility Client

A client-side Fabric utility mod for Minecraft 26.2.

This project is intentionally focused on quality-of-life and visual features:
- ClickGUI with module toggles and per-module settings
- Keybind for every module, accepting keyboard keys and mouse buttons
- In-GUI colour picker (saturation square + hue slider) for every colour setting
- HUD (FPS, coordinates, facing, session time) with a drag-to-move button
- Licence key system with server-side validation, four plans and an offline grace window
- Website with a `#admin` key-issuing panel
- Player Radar with radius, rotation, names, distance and per-dot height colouring
- Crosshair with 10 models (Plus, Dot, Circle, Cross, T-Shape, X, Ring, Diamond, Arrow, None) and vanilla crosshair removal
- Fullbright
- No Particles (client-side particle filter applied at the particle engine)
- No Dropped Items (client-side entity render filter)
- Block Highlight with colour picker, Border/Full style, thickness and fill opacity
- Anti Leaker resource pack that repaints leaky blocks with the netherite texture
- Custom Break Animation with Classic, Neon, Minimal, Pulse and Smooth presets,
  remapped crack stage plus a camera-facing progress bar on the broken block
- Config screen with Save, Load, and Reset actions
- Snap Turn that snaps to exact angles (defaults to 90 degrees) on all four arrow keys
- Auto Walk with straight, left, right and backward directions
- Click Holder that keeps left or right mouse held down
- Auto Villager Trader that trades the offer you click until that villager runs out
- Free Look: camera turns without turning your body
- Free Cam: detached camera you can fly around with
- Zoom
- Persistent JSON configuration
- Client commands

It does not include combat automation, x-ray, reach/aim assist, packet spoofing, or anti-cheat bypasses.

## Build

Use Java 25 and the included Gradle wrapper:

```text
.\gradlew.bat build
```

The built mod is written to `build/libs/utility-client-0.1.0.jar`.

Install the jar in the Fabric 26.2 profile's `mods` folder. Press **Insert** in-game or run `/utility` to open the menu. Left-click a module card to toggle it, or right-click the card to open its settings.

## Keybinds

Every module has a keybind row in its settings screen. Click the key box, then press any
keyboard key or mouse button; `ESC` cancels, `Delete` clears the binding and the small `x`
button clears it immediately. Keybinds also work while the menu is closed, and are stored in
the config file next to the module states.

## Moving the HUD

Open the HUD module settings and click **MOVE**. The menu closes, the mouse is released and the
HUD follows the cursor. Left-click to place it, `ESC` or right-click cancels and puts it back.

## Colour picker

Any setting drawn as a coloured swatch can be edited in place: click **pick** to open the picker,
drag inside the square for saturation/brightness and drag the bar for hue. **close** collapses it
again. Colours are stored as integers in the config file.

## Snap Turn

Arrow keys snap the camera to exact angles instead of adding a fixed offset. Standing at 37
degrees and pressing right snaps you to 90, pressing again to 180, and so on, so the camera
always ends up on a round multiple of the snap angle.

| Key | Default action |
| --- | --- |
| Left / Right | Snap a horizontal turn, `Turn angle` degrees (90 by default) |
| Up / Down | Look up / down in `Look angle` steps (45 by default), landing on exact angles |

The `Up / Down keys` setting decides what the vertical arrows do:

| Mode | Up / Down |
| --- | --- |
| Look up / down (default) | Snaps your view up and down to exact angles, clamped to straight up and down |
| Snap turn | Acts as a horizontal snap, same as left and right |
| Turn around | Spins you 180 degrees |
| Ignore | Nothing |

`Look angle` takes 15, 30, 45, 60 or 90 degrees, and `Turn angle` takes 45, 90, 135 or 180.
Also `Invert turn` and `Level pitch`, which only applies after a turn and never overrides a
deliberate look up or down.

## Auto Walk

Keeps a movement key held for you so you do not have to hold it. `Direction` picks `Straight`,
`Left`, `Right` or `Backward`, and `Always sprint` sprints while walking. Your real key presses
still work alongside it, so you can jump or strafe on top of the held direction.

It only changes the movement input the game already sends, so nothing is sent that pressing the
key yourself would not have sent.

## Click Holder

Keeps a mouse button held down so you do not have to hold it.

- `Hold left click` — attack and mining
- `Hold right click` — use, place and eat

This is a holder, not a clicker. The button is pressed once when the holder switches on and then
kept in the held state, which is exactly what happens when you hold the mouse yourself: the game
gets one press and then keeps acting on the hold. No timed or repeating clicks are produced, so it
cannot hit anything the game would not have hit while you held the button.

The hold is released automatically when you switch the module off, when a menu opens (so
inventory clicks keep working) and when you leave a world. It also follows your own keybinds, so
if you have moved attack or use to another key the holder presses that key instead.

## Categories

The sidebar uses these five, in this order:

| Category | Modules |
| --- | --- |
| Combat | none, and never will be |
| Movement | Auto Walk, Free Look, Free Cam, Snap Turn |
| DonutSMP | Anti Leaker |
| Visuals | Block Highlight, Crosshair, Custom Break Animation, Fullbright, No Dropped Items, No Particles, Zoom |
| Misc | Auto Villager Trader, Click Holder, HUD, Player Radar |

## Licence keys

The menu only opens with a working key. See [DEPLOY.md](DEPLOY.md) for the full setup.

- Four plans: 1 day, 1 week, 1 month, permanent
- Validated server side, so keys can be revoked without shipping a new build
- Cached for 6 hours, plus a 72 hour grace window if the key server is unreachable
- The profile card in the sidebar shows the plan and time remaining
- `/utilitykey` reopens the activation screen

Be aware of the limit: the mod runs on the buyer's machine, so a determined person can
remove the check from the jar. A key stops casual sharing, which is most of it, but it is not
unbreakable and should not be sold as though it is.

## Auto Villager Trader

Open a villager, click the trade you want, and it keeps trading that offer until the villager runs
out of it. Then the window closes itself.

It starts on the offer you clicked, not one it picked for you. Nothing happens until you select a
trade, so it never trades something you did not choose.

How it knows a trade is finished: a villager removes an exhausted offer from its list, so the list
getting shorter is the signal. The offer's own out of stock flag is checked as a second opinion,
but the list length is what it trusts, because the client can keep a stale reference to an offer
the server has already removed.

It is deliberately confined to the trade window:

- It only acts while a villager screen is open, so it can never walk you to a villager or open
  a trade on its own
- Each trade is the same click you would make on the result slot, through the normal container
  click handler
- `Hard cap` stops after a set number of trades even if the villager still has stock. This is not
  optional: a server villager that restocks would otherwise drain money indefinitely
- `Stall timeout` stops if the offer count stops changing, which covers being unable to afford the
  next trade
- `Close when done` closes the window once the offer is used up
- `Trade when full` off means it stops before your inventory cannot fit another result

Settings: `Delay`, `Hard cap`, `Stall timeout`, `Close when done`, `Trade when full`,
`Show status`.

## Anti Leaker

Some blocks report a nearby miner to other players. On servers where that matters this module
generates a small resource pack that repaints those blocks with the netherite block texture so you
can tell at a glance what you are looking at.

Switching the module on writes `resourcepacks/utilityclient-anti-leaker.zip`, enables it and
reloads your resource packs, so there is nothing to do by hand.

Blocks repainted:

- `deepslate`, `cobbled deepslate`, `polished deepslate`, `deepslate bricks`, `cracked deepslate
  bricks`, `deepslate tiles`, `cracked deepslate tiles`, `chiseled deepslate`
- `tuff`, `tuff bricks`, `chiseled tuff`, `polished tuff`, `chiseled tuff bricks`
- `gravel`, `bedrock`
- every deepslate ore (coal, copper, gold, redstone, emerald, lapis, diamond, iron)

`Include deepslate ores` turns the ore textures off or on, and `Show status` prints a line in chat
when the pack is written. The pack is an ordinary client side texture pack, nothing is sent to the
server.

## Free Look

Bind a key to the **Free Look** module to detach the camera from your body. Your view turns
freely while your character keeps facing the direction it was already walking, so strafing and
backpedalling stay predictable.

Settings: `Sensitivity` (mouse multiplier), `Lock vertical` (freeze pitch), `Perspective`,
`Camera through walls` and `Snap on disable` (turn your body to face where you were looking when
you switch it off).

`Camera through walls` is on by default. Vanilla normally raycasts around the third person camera
and pulls it towards you whenever a wall gets in the way, which is very annoying while looking
around. This stops that, so the camera keeps its distance and passes through blocks instead. Turn
it off if you would rather not see through terrain.

Free Look switches you to third person automatically, because the point of it only really reads
from outside the body. The `Perspective` setting chooses `Third person back` (default),
`Third person front`, or `Keep my perspective` if you would rather stay in first person. Whatever
view you were in before is put back when you switch Free Look off, and if you press F5 yourself in
the meantime your choice is respected instead.

Note that mining and attacking still use your body's facing, not the camera, which is the normal
Free Look behaviour.

## Free Cam

Bind a key to the **Free Cam** module to detach the camera completely and fly it around:

| Key | Movement |
| --- | --- |
| W / S | Forward and back, following where the camera looks |
| A / D | Strafe left and right |
| Space | Up |
| Left Shift | Down |
| Ctrl | Three times faster |

Settings: `Speed` (blocks per second), `Vertical movement`, `Ctrl to speed up` and
`Snap on disable` (your body turns to face where the camera was pointing).

Both modules are purely client side rendering. The server still sees your player standing exactly
where they were, and no packets are changed.
