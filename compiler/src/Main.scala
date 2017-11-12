import de.sciss.synth, synth._
import Ops._, ugen._

object Main {
  var s: Server = _
  var df: SynthDef = _
  var x: Synth = _

  def main(args: Array[String]): Unit = {
    println(System.getenv)
    val cfg = Server.Config()
    cfg.pickPort()
    Server.run(cfg) { s =>
      Main.s = s
      s.dumpOSC()
      val df = SynthDef("AnalogBubbles") {
        // val f1 = "freq1".kr(0.4)
        // val f2 = "freq2".kr(8)
        // val d  = "etune".kr(0.90375)
        // val f  = LFSaw.ar(f1).madd(24, LFSaw.ar(Seq(f2, f2 * d)).madd(3, 80)).midicps // glissando function
        // val f = LFSaw.kr(0.4).madd(24, LFSaw.kr(Seq(8, 7.23)).madd(3, 80)).midicps
        // val x  =
        Out.ar(0, CombN.ar(SinOsc.ar(LFSaw.kr("freq1".kr(0.4)).madd(24, LFSaw.kr(Seq(8, 7.23)).madd(3, 80)).midicps) * 0.04, 0.2, 0.2, 4)) // echoing sine wave
      }
      val x = df.play()
      Main.df = df
      Main.x = x
    }

    println("duckduck")
  }
}
