package MedianFilter

import java.awt.image.BufferedImage
import java.io.File

import akka.actor.{Actor, ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import javax.imageio.ImageIO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success}

object MedianFilter{
  val WINDOW_WIDTH: Int = 9
  val WINDOW_HEIGHT: Int = 9
  val windowSize: Int = WINDOW_WIDTH * WINDOW_HEIGHT
  val edgeX: Int = WINDOW_WIDTH / 2
  val edgeY: Int = WINDOW_HEIGHT / 2

  def concurrent(img: BufferedImage, log: LoggingAdapter): Unit ={
    log.info("Starting concurrent execution")
    val t1 = System.nanoTime

    val w = img.getWidth
    val h = img.getHeight

    // create new image of the same size
    val out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)

    val partition_1 = Future { concurrentHelper(img, out, edgeX, w/2, edgeY, h/2) }
    val partition_2 = Future { concurrentHelper(img, out, w/2, w-edgeX, edgeY, h/2) }
    val partition_3 = Future { concurrentHelper(img, out, edgeX, w/2, h/2, h-edgeY) }
    val partition_4 = Future { concurrentHelper(img, out, w/2, w-edgeX, h/2, h-edgeY) }

    val aggregatedFuture = for{
      result1 <- partition_1
      result2 <- partition_2
      result3 <- partition_3
      result4 <- partition_4
    } yield (result1, result2, result3, result4)

    aggregatedFuture onComplete {
      case Success(s) =>
        // save image to file
        ImageIO.write(out, "jpg", new File("concurrent-filter.jpg"))
        val duration = (System.nanoTime - t1) / 1e9d
        log.info("Ended concurrent execution in: {} sec", duration)
      case Failure(t) =>
        log.info("An error has occurred: {}", t.getMessage)
    }
  }

  def concurrentHelper(img: BufferedImage, out: BufferedImage, startX: Int, endX: Int, startY: Int, endY: Int): Int ={
    val window = new Array[Int](windowSize)
    val windowIndices = window.indices

    for (x <- startX until endX) {
      for (y <- startY until endY){
        windowIndices.foreach(i => window(i) = img.getRGB(x + i%WINDOW_WIDTH - edgeX, y + i/WINDOW_HEIGHT - edgeY))
        window.sortInPlace()
        out.setRGB(x, y, window(windowSize / 2))
      }
    }
    0
  }

  def serial(img: BufferedImage, log: LoggingAdapter): Unit ={
    log.info("Starting serial execution")
    val t1 = System.nanoTime

    val width = img.getWidth
    val height = img.getHeight

    val edgeX = WINDOW_WIDTH / 2
    val edgeY = WINDOW_HEIGHT / 2
    val sliding_window = new Array[Int](WINDOW_WIDTH * WINDOW_HEIGHT)

    // create new image of the same size
    val result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    for (x <- edgeX until width - edgeX) {
      for (y <- edgeY until height - edgeY){
        var i = 0
        for (fx <- 0 until WINDOW_WIDTH) {
          for (fy <- 0 until WINDOW_HEIGHT) {
            sliding_window(i) = img.getRGB(x + fx - edgeX, y + fy - edgeY)
            i += 1
          }
        }
        sliding_window.sortInPlace()
        result.setRGB(x, y, sliding_window(WINDOW_WIDTH * WINDOW_HEIGHT / 2))
      }
    }

    // save image to file
    ImageIO.write(result, "jpg", new File("serial-filter.jpg"))
    val duration = (System.nanoTime - t1) / 1e9d

    log.info("Ended serial execution in: {} sec", duration)
  }
}

case class SerialMedianFilter(img: BufferedImage)
case class ConcurrentMedianFilter(img: BufferedImage)

class Client extends Actor{
  val log: LoggingAdapter = Logging(context.system, this)

  def receive: Receive = {
    case SerialMedianFilter(img) =>
      log.info("Sending SerialMedianFilter message to MockServer")
      context.actorOf(Props[Server], "serial-server") ! SerialMedianFilter(img)
    case ConcurrentMedianFilter(img) =>
      log.info("Sending ConcurrentMedianFilter message to MockServer")
      context.actorOf(Props[Server], "concurrent-server") ! ConcurrentMedianFilter(img)
  }
}

class Server extends Actor{
  val log: LoggingAdapter = Logging(context.system, this)

  def receive: Receive = {
    case SerialMedianFilter(img) =>
      MedianFilter.serial(img, log)
    case ConcurrentMedianFilter(img) =>
      MedianFilter.concurrent(img, log)
  }
}


object Main extends App {
  val system = ActorSystem("MedianFilter")
  val client = system.actorOf(Props[Client], "client")
  val img: BufferedImage = ImageIO.read(new File("test.jpg"))

  client ! SerialMedianFilter(img)
  client ! ConcurrentMedianFilter(img)
}
