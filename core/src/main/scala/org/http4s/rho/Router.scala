package org.http4s
package rho

import bits.PathAST._
import bits.HeaderAST._
import bits.QueryAST.QueryRule
import org.http4s.rho.bits.{HeaderAppendable, HListToFunc}

import shapeless.{::, HList}
import shapeless.ops.hlist.Prepend

/** Provides the operations for generating a router
  *
  * @param method request methods to match
  * @param path path matching stack
  * @param validators header validation stack
  * @tparam T cumulative type of the required method for executing the router
  */
case class Router[T <: HList](method: Method,
                               val path: PathRule,
                               val query: QueryRule,
                               validators: HeaderRule)
                       extends RouteExecutable[T]
                          with HeaderAppendable[T]
{

  type Self = Router[T]

  override def >>>[T2 <: HList](v: TypedHeader[T2])(implicit prep1: Prepend[T2, T]): Router[prep1.Out] =
    Router(method, path, query, HeaderAnd(validators, v.rule))

  override def makeAction[F](f: F, hf: HListToFunc[T, F]): RhoAction[T, F] =
    new RhoAction(this, f, hf)

  def decoding[R](decoder: Decoder[R]): CodecRouter[T, R] = CodecRouter(this, decoder)
}

case class CodecRouter[T <: HList, R](router: Router[T], decoder: Decoder[R])
           extends HeaderAppendable[T]
           with RouteExecutable[R::T]
{
  type Self = CodecRouter[T, R]

  override def >>>[T2 <: HList](v: TypedHeader[T2])(implicit prep1: Prepend[T2, T]): CodecRouter[prep1.Out,R] =
    CodecRouter(router >>> v, decoder)

  override def makeAction[F](f: F, hf: HListToFunc[R::T, F]): RhoAction[R::T, F] =
    new RhoAction(this, f, hf)

  override def path: PathRule = router.path

  override def method: Method = router.method

  override def query: QueryRule = router.query

  def decoding(decoder2: Decoder[R]): CodecRouter[T, R] = CodecRouter(router, decoder.or(decoder2))

  override val validators: HeaderRule = {
    if (!decoder.consumes.isEmpty) {
      val mt = requireThat(Header.`Content-Type`){ h: Header.`Content-Type`.HeaderT =>
        decoder.consumes.find(_ == h.mediaType).isDefined
      }
      HeaderAnd(router.validators, mt.rule)
    } else router.validators
  }
}
