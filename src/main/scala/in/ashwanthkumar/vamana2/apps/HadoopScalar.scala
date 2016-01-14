package in.ashwanthkumar.vamana2.apps

import in.ashwanthkumar.vamana2.core._
import org.joda.time.DateTime

import scala.concurrent.duration.Duration

case class HDemand(map: Double, reduce: Double) extends Demand {
  def quantity = map + reduce
}
case class HSupply(map: Double, reduce: Double) extends Supply {
  def available = map + reduce
}

/**
 * Initial version of HadoopScalar that scales up the cluster to ClusterConfiguration.maxSize (if there's demand)
 * or scales down the cluster to ClusterConfiguration.minSize.
 *
 * We monitor for scale down as no demand for the last 30 min.
 *
 */
class HadoopScalar extends Scalar[HDemand, HSupply] {
  /**
   * @inheritdoc
   */
  override def requiredNodes(demand: HDemand, supply: HSupply, ctx: Context): Int = {
    // If the demand is nothing, scale down to cluster min size
    // If the cluster is running with min capacity and demand > supply, scale it up to max size
    // else keep the cluster intact
    if (demand.quantity == 0.0) ctx.cluster.minNodes
    else if (demand.quantity > supply.available && ctx.currentSize < ctx.cluster.maxNodes) ctx.cluster.maxNodes
    else ctx.currentSize
  }

  /**
   * @inheritdoc
   */
  override def demand(metrics: List[Metric]): HDemand = {
    val (mapDemand, reduceDemand) = mapAndReduceMetrics(metrics, "map_demand", "reduce_demand")
    HDemand(mapDemand, reduceDemand)
  }

  /**
   * @inheritdoc
   */
  override def supply(metrics: List[Metric]): HSupply = {
    val (mapSupply, reduceSupply) = mapAndReduceMetrics(metrics, "map_supply", "reduce_supply")
    HSupply(mapSupply, reduceSupply)
  }

  private[apps] def mapAndReduceMetrics(metrics: List[Metric], mapMetric: String, reduceMetric: String): (Double, Double) = {
    val metricsInDemand = metrics.map(_.name).toSet
    require(Set(mapMetric, reduceMetric).subsetOf(metricsInDemand), "we need " + mapMetric + " and " + reduceMetric)

    val mapDemand = metrics.filter(_.name == mapMetric).head
    val reduceDemand = metrics.filter(_.name == reduceMetric).head

    val mapMetrics = mapDemand.points.map(_.value).sum
    val reduceMetrics = reduceDemand.points.map(_.value).sum
    (mapMetrics, reduceMetrics)
  }
}

object HadoopScalar {
  def apply(): HadoopScalar = new HadoopScalar
}
