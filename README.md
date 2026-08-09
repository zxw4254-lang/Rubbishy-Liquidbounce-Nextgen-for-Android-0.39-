<div align="center">
<p>
    <img width="200" src="https://raw.githubusercontent.com/CCBlueX/LiquidCloud/master/LiquidBounce/liquidbounceLogo.svg">
</p>

[Website](https://liquidbounce.net) |
[Forum](https://forums.ccbluex.net) |
[Discord](https://liquidbounce.net/discord) |
[YouTube](https://youtube.com/CCBlueX) |
[X](https://x.com/CCBlueX)
</div>

LiquidBounce is a free and open-source mixin-based injection hacked client using the Fabric API for Minecraft.

## Issues

If you notice any bugs or missing features, you can let us know by opening an
issue [here](https://github.com/CCBlueX/LiquidBounce/issues).

## License

This project is subject to the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html). This
does only apply for source code located directly in this clean repository. During the development and compilation
process, additional source code may be used to which we have obtained no rights. Such code is not covered by the GPL
license.

For those who are unfamiliar with the license, here is a summary of its main points. This is by no means legal advice
nor legally binding.

*Actions that you are allowed to do:*

- Use
- Share
- Modify

*If you do decide to use ANY code from the source:*

- **You must disclose the source code of your modified work and the source code you took from this project. This means
  you are not allowed to use code from this project (even partially) in a closed-source (or even obfuscated)
  application.**
- **Your modified application must also be licensed under the GPL**

## Setting up a Workspace

LiquidBounce uses Gradle; to make sure that it is installed properly, you can
check [Gradle's website](https://gradle.org/install/). It also requires [Node.js](https://nodejs.org) to be installed for
our [theme](https://github.com/CCBlueX/LiquidBounce/tree/nextgen/src-theme).

1. Clone the repository using `git clone --recurse-submodules https://github.com/CCBlueX/LiquidBounce`.
2. CD into the local repository. (`cd LiquidBounce`)
3. Run `./gradlew genSources` for better development experience (Optional).
4. Open the folder as a Gradle project in your preferred IDE.
5. Run the client. (`./gradlew runClient`)

## Additional libraries

### Mixins

Mixins can be used to modify classes at runtime before they are loaded. LiquidBounce uses it to inject its code into the
Minecraft client. This way, none of Mojang's copyrighted code is shipped. If you want to learn more about it, check out
its [Documentation](https://docs.spongepowered.org/5.1.0/en/plugin/internals/mixins.html).

## Android support (PojavLauncher / ZalithLauncher)

LiquidBounce can run inside Android launchers that execute the desktop Java edition of Minecraft, such as
[PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) and its forks
(e.g. [ZalithLauncher](https://github.com/ZalithLauncher/ZalithLauncher)).

Installation is the same as on desktop: place the mod JAR into `mods/` together with Fabric Loader, Fabric API and
Fabric Language Kotlin, then launch with a Java 21+ runtime (as required by the targeted Minecraft version).

Platform specific behaviour on Android:

- The web-based UI (ClickGUI, HUD editor, marketplace, themes) is rendered through JCEF/CEF (Chromium), which has no
  Android build. On Android the browser backend is automatically disabled — configure the client via chat commands
  instead (e.g. `.modules`, `.set <module> <value>`, `.config`, `.script`).
- The deep learning feature (DJL PyTorch engine) downloads platform native libraries and depends on desktop-only MCEF
  classes; it is disabled on Android by default. Set `LB_DL_FORCE=true` (or the
  `net.ccbluex.liquidbounce.deeplearning.force` system property) to attempt it anyway.
- Opening URLs uses an Android `ACTION_VIEW` intent; opening folders in a file manager is not supported and is skipped.
- Native file dialogs (TinyFileDialogs) are unavailable; file picker values simply return no selection.
- System fonts resolve to Android fonts (Roboto / Noto Sans CJK) instead of desktop fonts.
- The fatal-error dialog is replaced by console logging.

You can still force-disable the browser UI on desktop with the `LB_BROWSER_SKIP=true` environment variable or the
`net.ccbluex.liquidbounce.browser.skip=true` system property.

### Building an Android-flavoured JAR

By default the build behaves exactly like the desktop release (MCEF/JCEF is packaged). To produce a slimmer JAR that
excludes the desktop-only JCEF/MCEF runtime — all MCEF usages are already guarded at runtime — build with:

```shell
./gradlew build -Plb.android=true
# or
LB_ANDROID_BUILD=1 ./gradlew build
```

## Contributing

We appreciate contributions. So if you want to support us, feel free to make changes to LiquidBounce's source code and
submit a pull request.

## Stats

![Alt](https://repobeats.axiom.co/api/embed/ad3a9161793c4dfe50934cd4442d25dc3ca93128.svg "Repobeats analytics image")

## Imprint

**CCBlueX**  
Vahrenwalder Str. 269A
30179 Hanover
Germany

**Owner and responsible for the content:** Marco Beyer
