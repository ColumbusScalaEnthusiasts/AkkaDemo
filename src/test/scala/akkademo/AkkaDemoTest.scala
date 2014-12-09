import akka.actor.{Props, Actor, ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.path

import scala.collection.mutable.ListBuffer


case object Increment
case object QueryValue
case class ValueAccumulator (respondTo: ActorRef, value: Long, multiplier: Long)
case class Value (value: Long)


class Digit (neighborOpt: Option[ActorRef]) extends Actor {

  private var value = 0

  def receive = {
    case Increment => handleIncrement ()
    case QueryValue => handleQueryValue ()
    case msg: ValueAccumulator => handleValueAccumulator (msg)
  }

  private def handleIncrement (): Unit = {
    value += 1
    if (value > 9) {
      value = 0
      neighborOpt match {
        case Some (neighbor) => neighbor ! Increment
        case None =>
      }
    }
  }

  private def handleQueryValue (): Unit = {
    neighborOpt match {
      case Some(neighbor) => neighbor ! ValueAccumulator(sender(), value, 1)
      case None => sender ! Value (value)
    }
  }

  private def handleValueAccumulator (msg: ValueAccumulator): Unit = {
    val multiplier = msg.multiplier * 10
    val newValue = (multiplier * value) + msg.value
    neighborOpt match {
      case Some(neighbor) => neighbor ! ValueAccumulator(msg.respondTo, newValue, multiplier)
      case None => msg.respondTo ! Value (newValue)
    }
  }
}


class AkkaDemoTest(_system: ActorSystem)
  extends TestKit(_system) with ImplicitSender with path.FunSpecLike {

  def this() = this(ActorSystem ("AkkaDemoTest"))

  describe ("A Digit") {
    val digit = system.actorOf (Props (classOf[Digit], None))

    describe ("when queried about its value") {
      digit ! QueryValue

      it("responds with zero") {
        expectMsgAllOf (Value (0L))
      }

      describe("and sent an Increment message and then queried again") {
        digit ! Increment
        digit ! QueryValue

        it("now has a value of one") {
          expectMsgAllOf (Value(0L), Value(1L))
        }

        describe ("and incremented eight more times and queried") {
          (1 to 8).foreach {_ => digit ! Increment}
          digit ! QueryValue

          it ("has a value of nine") {
            expectMsgAllOf (Value(0L), Value(1L), Value (9L))
          }

          describe ("and incremented one more time") {
            digit ! Increment
            digit ! QueryValue

            it ("has rolled over to zero") {
              expectMsgAllOf (Value (0L), Value (1L), Value (9L), Value (0L))
            }
          }
        }
      }
    }
  }

  describe ("A pair of digits") {
    val tens = system.actorOf (Props (classOf[Digit], None))
    val ones = system.actorOf (Props (classOf[Digit], Some (tens)))

    describe ("when the ones digit is incremented to nine") {
      (1 to 9).foreach {_ => ones ! Increment}
      ones ! QueryValue

      it ("respond with a 9") {
        expectMsgAllOf (Value (9L))
      }

      describe ("and once more") {
        ones ! Increment
        ones ! QueryValue

        it ("responds with ten") {
          expectMsgAllOf (Value (9L), Value (10L))
        }
      }
    }

    describe ("when the digits are set to 99") {
      (1 to 9).foreach {_ => ones ! Increment}
      (1 to 9).foreach {_ => tens ! Increment}

      describe ("and incremented one more time") {
        ones ! Increment
        ones ! QueryValue

        it ("responds with zero") {
          expectMsgAllOf (Value (0L))
        }
      }
    }
  }

  system.shutdown ()
}
