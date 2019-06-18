// See LICENSE for license details.

package firrtl.transforms.clockfinder

import firrtl.analyses._
import firrtl.annotations._
import firrtl.{CircuitForm, CircuitState, MidForm, Transform}

import scala.collection.mutable

/** Finds clock sources of specific signals
  *
  * For all targets contained within a [[GetClockSources]] annotation, calculate their clock source
  * Produces a [[ClockSources]] annotation containing their mapping
  *
  * If target is:
  *   - ReferenceTarget; calculate clock source
  *   - InstanceTarget; calculate clock source of all input ports to that instance
  *   - ModuleTarget; calculate clock source of all output ports
  *   - CircuitTarget; calculate clock source of all output ports of all modules
  */
class ClockFinderTransform() extends Transform {

  override def inputForm: CircuitForm = MidForm
  override def outputForm: CircuitForm = MidForm

  override def execute(state: CircuitState): CircuitState = {

    val targets = mutable.ArrayBuffer.empty[CompleteTarget]
    val signalsToClocks = mutable.HashMap.empty[ReferenceTarget, Set[ReferenceTarget]]
    state.annotations.foreach {
      case GetClockSources(ts, top) =>
        targets ++= ts
        if(top) targets += CircuitTarget(state.circuit.main)
      case cs: ClockSources => signalsToClocks ++= cs.signalsToClockRefs
      case other =>
    }

    if(targets.nonEmpty) {
      println("Running Clock Finder")
      val (ms, finder) = firrtl.Utils.time {
        new ClockFinder(ConnectionGraph(state.circuit).reverseConnectionGraph, signalsToClocks.toMap)
      }
      println(s"Finished Building Clock Finder ($ms ms)")
      val (ms2, clockSources) = firrtl.Utils.time {
        ClockSources.buildFromRefs(finder.getClockSource(targets))
      }
      println(s"Finished Executing Clock Finder ($ms2 ms)")
      state.copy(annotations = clockSources +: state.annotations)
    } else {
      state
    }
  }
}