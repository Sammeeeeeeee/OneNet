# Shizuku instantiates the user service by reflection, in its own process.
-keep class io.github.sammeeeeeeee.onenet.shell.ShellService { <init>(...); }

# One UI calls these on the tile service by name to build the tile's panel.
-keepclassmembers class io.github.sammeeeeeeee.onenet.tile.InternetTileService {
    public *** sem*(...);
}
