plugins {
  id("fr.stardustenterprises.rust.wrapper")
}

rust {
  // Setting the global "default" vars
  release.set(true)

  // you can also use tools like "cross"
  command.set("cargo")

  // this will make cargo install automatically the required targets
  // `rustup targets add x86_64-pc-windows-gnu`
  cargoInstallTargets.set(true)

  // Defining per-targets
  targets {
    // Adds the default target
    this += defaultTarget()

    /*
    // Creates a named target with a custom file output
    create("win64") {
      target = "x86_64-pc-windows-gnu"
      outputName = "test64.dll"
    }

    // Custom target with different params than default
    create("macOS-x86") {
      target = "x86_64-apple-darwin"
      outputName = "libtest64.dylib"

      // Use other command for this target
      command = "cargo"
      env += "CC" to "o64-clang"
      env += "CXX" to "o64-clang++"
    }

    create("macOS-aarch64") {
      target = "aarch64-apple-darwin"
      outputName = "libtest64.dylib"

      command = "cargo"
      env += "CC" to "oa64-clang"
      env += "CXX" to "oa64-clang++"
    }
     */
  }
}