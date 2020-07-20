## About SpinalHDL

SpinalHDL is:
- A language to describe digital hardware 
- Compatible with EDA tools as it generate the corresponding VHDL/Verilog file
- Much more powerful than VHDL, Verilog and SystemVerilog by its syntaxes and features
- Much less verbose than VHDL, Verilog and SystemVerilog
- Not an HLS, nor based on the event driven paradigm
- Only generating what you asked it in a one to one way (no black-magic, no blackbox)
- Not introducing Area/Performance overhead in your design (versus a hand written VHDL/Verilog)
- Based on the RTL description paradigm, but can go much further
- Allowing you to use Object Oriented Programming and Functional Programming to elaborate your hardware and verify it
- Free and can be used in the industry without any license

## Links
- Documentation                  <br> https://spinalhdl.github.io/SpinalDoc-RTD/
- Presentation of the language   <br> https://spinalhdl.github.io/SpinalDoc-RTD/SpinalHDL/Getting%20Started/presentation.html
- SBT base project               <br> https://github.com/SpinalHDL/SpinalTemplateSbt
- Gradle base project            <br> https://github.com/SpinalHDL/SpinalTemplateGradle
- jupyter bootcamp               <br> https://github.com/SpinalHDL/Spinal-bootcamp
- Workshop                       <br> https://github.com/SpinalHDL/SpinalWorkshop
- Google group                   <br> https://groups.google.com/forum/#!forum/spinalhdl-hardware-description-language

[![Join the chat at https://gitter.im/SpinalHDL/SpinalHDL](https://badges.gitter.im/SpinalHDL/SpinalHDL.svg)](https://gitter.im/SpinalHDL/SpinalHDL?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Build Status](https://travis-ci.org/SpinalHDL/SpinalHDL.svg?branch=master)](https://travis-ci.org/SpinalHDL/SpinalHDL)

## Get it

SpinalHDL is simply a set of Scala libraries. Include them into your project and you're good to go! If you're unsure about what to do, simply clone one of our example projects (see links above).

### SBT (Scala build tool)

```scala
scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.github.spinalhdl" % "spinalhdl-core_2.11" % "latest.release",
  "com.github.spinalhdl" % "spinalhdl-lib_2.11" % "latest.release"
)
```

You can force to pick a specific SpinalHDL version by replacing the 'latest.release'. See https://github.com/SpinalHDL/SpinalTemplateSbt/blob/master/build.sbt

### Gradle

```kotlin
repositories {
	mavenCentral()
}

dependencies {
	compile group: 'com.github.spinalhdl', name: 'spinalhdl-core_2.11', version: '1.3.6'
	compile group: 'com.github.spinalhdl', name: 'spinalhdl-lib_2.11', version: '1.3.6'
}
```

### JAR

    https://oss.sonatype.org/content/groups/public/com/github/spinalhdl/spinalhdl-core_2.11/
    https://oss.sonatype.org/content/groups/public/com/github/spinalhdl/spinalhdl-lib_2.11/

The files are available [on Maven](https://mvnrepository.com/artifact/com.github.spinalhdl) as well.

## Changes logs

https://github.com/SpinalHDL/SpinalHDL/tags

## License

The SpinalHDL core is using the LGPL3 license while SpinalHDL lib is using the MIT one. That's for the formalities. But there is some practices statements of those license implications :

Your freedoms are :
- You can use SpinalHDL core and lib in your closed/commercial projects.
- The generated RTL is yours (.vhd/.v files)
- Your hardware description is yours (.scala files)

Your obligations (and my wish) are :
- If you modify the SpinalHDL core (the compiler itself), please, share your improvements.

Also, SpinalHDL is provided "as is", without warranty of any kind.
