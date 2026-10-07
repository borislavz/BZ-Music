plugins {
    id("com.android.application")
}
configurations.maybeCreate("libCheck")
dependencies {
    "libCheck"("org.videolan.android:libvlc-all:3.6.0")
}
tasks.register("resolveVlc") {
    doLast {
        configurations["libCheck"].files.forEach { println(it.name + " " + it.length()) }
    }
}
