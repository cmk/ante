package com.ante.wavelet

import org.scalatest.FlatSpec
import org.scalatest.Matchers

object Derivatives
{
  def log2(input : Double) : Double =
    Math.log(input) / Math.log(2)
  
  def FirstDerivative(input : Array[Double]) : Array[Double] =
  {
    val num_twos = log2(input.length).toInt
    
    val result : Array[Double] = (for (i <- 0 until num_twos)
      yield 0.0).toArray
    for (i <- 1 to num_twos)
	{
	  val index = num_twos - i
	  val pow_two = 2 << index
	  val diff = (input.last - input(input.length - pow_two)) / (pow_two - 1)
	  result(index) = diff
	}
    result
  }
  def FirstDerivativeAdjusted(input : Array[Double]) : Array[Double] =
  {
    val result = FirstDerivative(input)
    var accumulated_diff = 0.0
    for (i <- 1 to result.length)
    {
      val index = result.length - i
      result(index) -= accumulated_diff
      accumulated_diff += result(index)
    }
    result
  }
  def SecondDerivative(input : Array[Double]) : Array[Double] =
  {
    val num_twos = log2(input.length - 1).toInt
    
    val result : Array[Double] = (for (i <- 0 until num_twos)
      yield 0.0).toArray
    for (i <- 1 to num_twos)
	{
	  val index = num_twos - i
	  val pow_two = 1 << index
	  val last = input.last
	  val first = input(input.size - 1 - 2 * pow_two)
	  val middle = input(input.size - 1 - pow_two)
	  val diff = (last + first - 2 * middle) / (pow_two * pow_two)
	  result(index) = diff
	}
    result
  }
}

class LogSpec extends FlatSpec with Matchers
{
    "log2" should "not round down" in
	{
      for (i <- 1 until 30)
      {
        Derivatives.log2(1 << i).toInt should be (i)
      }
	}
    "log2" should "round in the right direction" in
    {
      Derivatives.log2(17).toInt should be (4)
    }
}
class DerivativeSpec extends FlatSpec with Matchers
{
    "an increasing sequence" should "only have a first derivative on the longest sequence" in
    {
      val input = Array(0.0, 1.0, 2.0, 3.0, 4.0)
      Derivatives.FirstDerivativeAdjusted(input) should be (Array(0.0, 1.0))
      Derivatives.SecondDerivative(input) should be (Array(0.0, 0.0))
    }
    "x^2" should "be 2 in the second derivative" in
    {
      val input = (for (i <- 1 to 17) yield (i * i).toDouble).toArray
      Derivatives.SecondDerivative(input) should be (Array(2.0, 2.0, 2.0, 2.0))
    }
    "a spike at the end" should "show up in the short term" in
    {
      val input = Array(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 9.0)
      val result = Derivatives.FirstDerivativeAdjusted(input)
      val second = Derivatives.SecondDerivative(input)
      result.length should be (3)
      result(2) should be > result(1)
      result(1) should be < result(0)
      second(2) should be > 0.0
      second(2) should be < second(1)
      second(1) should be < second(0)
    }
    "a bigger spike" should "should override the long term" in
    {
      val result = Derivatives.FirstDerivativeAdjusted(Array(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 20.0))
      result.length should be (3)
      result(2) should be > result(1)
      result(1) should be < result(0)
      result(0) should be > result(2)
    }
    "a simple power series" should "show up different in first derivative than in second" in
    {
      val input = (for (i <- 0 to 16) yield (Math.pow(1.1, i))).toArray
      val result = Derivatives.FirstDerivativeAdjusted(input)
      val second = Derivatives.SecondDerivative(input)
      result.length should be (4)
      result(3) should be > result(2)
      result(2) should be > result(1)
      result(1) should be > result(0)
      second.length should be (4)
      second(3) should be < second(2)
      second(2) should be < second(1)
      second(1) should be < second(0)
    }
}
