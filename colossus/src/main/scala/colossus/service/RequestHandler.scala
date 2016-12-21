package colossus.service

import colossus.core._

class UnhandledRequestException(message: String) extends Exception(message)
class ReceiveException(message: String) extends Exception(message)
class RequestHandlerException(message: String) extends Exception(message)

object GenRequestHandler {

  type PartialHandler[C <: Protocol] = PartialFunction[C#Request, Callback[C#Response]]

  type Receive = PartialFunction[Any, Unit]

  type ErrorHandler[C <: Protocol] = PartialFunction[ProcessingFailure[C#Request], C#Response]

  type ParseErrorHandler[C <: Protocol] = PartialFunction[Throwable, C#Response]
}
import GenRequestHandler._

abstract class GenRequestHandler[P <: Protocol](val config: ServiceConfig, val serverContext: ServerContext) 
extends DownstreamEvents with HandlerTail with UpstreamEventHandler[ServiceUpstream[P]] {

  type Request = P#Request
  type Response = P#Response

  def this(context: ServerContext) = this(ServiceConfig.load(context.name), context)

  protected val server = serverContext.server
  def context = serverContext.context
  implicit val worker = context.worker

  private var _connectionManager: Option[ConnectionManager] = None

  protected def connection = _connectionManager.getOrElse {
    throw new RequestHandlerException("Cannot access connection before request handler is bound")
  }

  def setConnection(connection: ConnectionManager) {
    _connectionManager = Some(connection)
  }

  implicit val executor   = context.worker.callbackExecutor

  protected def handle: PartialHandler[P]
  protected def unhandledError: ErrorHandler[P] 

  protected def onError: ErrorHandler[P] = Map()

  private lazy val fullHandler: PartialHandler[P] = handle orElse {
    case req => Callback.failed(new UnhandledRequestException(s"Unhandled Request $req"))
  }

  def handleRequest(request: Request): Callback[Response] = fullHandler(request)
  private lazy val errorHandler: ErrorHandler[P] = onError orElse unhandledError

  def handleFailure(error: ProcessingFailure[Request]): Response = errorHandler(error)

  def tagDecorator: TagDecorator[P] = TagDecorator.default[P]
  def requestLogFormat : Option[RequestFormatter[Request]] = None



  protected def disconnect() {
    connection.disconnect()
  }

}
