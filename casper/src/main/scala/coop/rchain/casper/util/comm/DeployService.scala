package coop.rchain.casper.util.comm

import java.io.Closeable
import java.util.concurrent.TimeUnit

import com.google.protobuf.empty.Empty
import coop.rchain.casper.protocol._
import coop.rchain.models.Par
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import monix.eval.Task

trait DeployService[F[_]] {
  def deploy(d: DeployData): F[(Boolean, String)]
  def createBlock(): F[(Boolean, String)] //create block and add to Casper internal state
  def showBlock(q: BlockQuery): F[String]
  def showBlocks(q: BlocksQuery): F[String]
  def addBlock(b: BlockMessage): F[(Boolean, String)]
  def listenForDataAtName(request: DataAtNameQuery): F[ListeningNameDataResponse]
  def listenForContinuationAtName(
      request: ContinuationAtNameQuery
  ): F[ListeningNameContinuationResponse]
}

object DeployService {
  def apply[F[_]](implicit ev: DeployService[F]): DeployService[F] = ev
}

class GrpcDeployService(host: String, port: Int, maxMessageSize: Int)
    extends DeployService[Task]
    with Closeable {

  private val channel: ManagedChannel =
    ManagedChannelBuilder
      .forAddress(host, port)
      .maxInboundMessageSize(maxMessageSize)
      .usePlaintext(true)
      .build
  private val blockingStub = DeployServiceGrpc.blockingStub(channel)

  def deploy(d: DeployData): Task[(Boolean, String)] = Task.delay {
    val response = blockingStub.doDeploy(d)
    (response.success, response.message)
  }

  def createBlock(): Task[(Boolean, String)] =
    Task.delay {
      val response = blockingStub.createBlock(Empty())
      (response.success, response.message)
    }

  def showBlock(q: BlockQuery): Task[String] = Task.delay {
    val response = blockingStub.showBlock(q)
    response.toProtoString
  }

  def showBlocks(q: BlocksQuery): Task[String] = Task.delay {
    val response = blockingStub.showBlocks(q).toList

    val showResponses = response.map(bi => s"""
------------- block ${bi.blockNumber} ---------------
${bi.toProtoString}
-----------------------------------------------------
""").mkString("\n")

    val showLength =
      s"""
count: ${response.length}
"""
    showResponses + "\n" + showLength
  }

  def addBlock(b: BlockMessage): Task[(Boolean, String)] = Task.delay {
    val response = blockingStub.addBlock(b)
    (response.success, response.message)
  }

  def listenForDataAtName(request: DataAtNameQuery): Task[ListeningNameDataResponse] = Task.delay {
    blockingStub.listenForDataAtName(request)
  }

  def listenForContinuationAtName(
      request: ContinuationAtNameQuery
  ): Task[ListeningNameContinuationResponse] =
    Task.delay {
      blockingStub.listenForContinuationAtName(request)
    }

  override def close(): Unit = {
    val terminated = channel.shutdown().awaitTermination(10, TimeUnit.SECONDS)
    if (!terminated) {
      println(
        "warn: did not shutdown after 10 seconds, retrying with additional 10 seconds timeout"
      )
      channel.awaitTermination(10, TimeUnit.SECONDS)
    }
  }
}
