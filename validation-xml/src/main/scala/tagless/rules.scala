package jto.validation
package v3.tagless
package xml

import jto.validation.xml.Rules
import scala.xml._
// import shapeless.tag.@@
// import cats.syntax.cartesian._

trait RulesGrammar
  extends XmlGrammar[Node, Rule]
  with RuleConstraints
  with RulesTypeclasses[Node] {
  self =>

  type Out = Node
  type Sub = Node
  type P = RulesGrammar

  def mapPath(f: Path => Path): P =
    new RulesGrammar {
      override def at[A](p: Path)(k: => Rule[Option[_ >: Out <: Node],A]): Rule[Node, A] =
        self.at(f(p))(k)
    }

  @inline private def search(path: Path, node: Node): Option[Node] = path.path match {
      case KeyPathNode(key) :: tail =>
        (node \ key).headOption.flatMap(childNode =>
              search(Path(tail), childNode))

      case IdxPathNode(idx) :: tail =>
        (node \ "_")
          .lift(idx)
          .flatMap(childNode => search(Path(tail), childNode))

      case Nil => Some(node)
    }

  def at[A](p: Path)(k: => Rule[Option[_ >: Out <: Node], A]): Rule[Node, A] =
    Rule(p) { i => k.validate(search(p, i)) }

  def opt[A](implicit K: Rule[_ >: Out <: Node, A]): Rule[Option[Node], Option[A]] =
    Rule(Path) {
      case Some(x) =>
        K.validate(x).map(Option.apply)
      case None =>
        Valid(None)
    }

  def req[A](implicit K: Rule[_ >: Out <: Node, A]): Rule[Option[Node], A] =
    Rule(Path) {
      case Some(x) =>
        K.validate(x)
      case None =>
        Invalid(Seq(Path -> Seq(ValidationError("error.required"))))
    }

  implicit def int = Rules.nodeR(Rules.intR)
  implicit def string = Rules.nodeR(Rule.zero)
  implicit def bigDecimal = Rules.nodeR(Rules.bigDecimal)
  implicit def boolean = Rules.nodeR(Rules.booleanR)
  implicit def double = Rules.nodeR(Rules.doubleR)
  implicit def float = Rules.nodeR(Rules.floatR)
  implicit def jBigDecimal = Rules.nodeR(Rules.javaBigDecimalR)
  implicit def long = Rules.nodeR(Rules.longR)
  implicit def short = Rules.nodeR(Rules.shortR)

  implicit def seq[A](implicit k: Rule[_ >: Out <: Node, A]) = Rules.pickSeq(k)
  implicit def list[A](implicit k: Rule[_ >: Out <: Node, A]) = Rules.pickList(k)
  implicit def array[A: scala.reflect.ClassTag](implicit k: Rule[_ >: Out <: Node, A]) = Rules.pickList(k).map(_.toArray)
  implicit def map[A](implicit k: Rule[_ >: Out <: Node, A]) =
    Rule(Path) { n =>
      import cats.instances.list._
      import cats.syntax.traverse._
      val ns =
        for {
          c <- n.child
          l = c.label
        } yield k.repath(_ \ l).validate(c).map(l -> _)
      ns.toList.sequenceU.map(_.toMap)
    }

  implicit def traversable[A](implicit k: Rule[_ >: Out <: Node, A]) = Rules.pickTraversable(k)

  def toGoal[Repr, A] = _.map { Goal.apply }

  def attr[A](key: String)(implicit r: Rule[Node, A]): Rule[Node, A] =
     Rule.fromMapping[Node, Node] { node =>
        node.attribute(key).flatMap(_.headOption) match {
          case Some(value) => Valid(value)
          case None => Invalid(Seq(ValidationError("error.required")))
        }
      }.andThen(r)

}

object RulesGrammar extends RulesGrammar