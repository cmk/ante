package com.ante.wavelet

import net.liftweb.json
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonAST.JDouble
import net.liftweb.json.JsonAST.JInt
import net.liftweb.json.JsonAST.JArray
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import scala.collection.mutable.ArrayBuilder

object StaticWaveletTransform
{
  case class WaveletSource(iteration : Int, value : Float)
  
  // this function is kinda messy. it does this:
  // if you give me values 1, 3, _, 5 (meaning one value is missing)
  // then it will return the array 1, 3, 4, 5
  // it does a linear interpolation to fill the gaps
  // unfortunately I can only come up with a clean solution involving std::vector,
  // std::sort and std::lower_bound. there is probably also a clean solution in
  // scala but for now I wrote an ugly solution in here...
   def build_array(values : Array[WaveletSource], iteration : Int, num_values : Int) : Array[Float] =
   {
    var previous = 0
    var next = 0
    val result = new ArrayBuilder.ofFloat
    def position(other_iteration : Int) : Int = (num_values - 1) - (iteration - other_iteration)
    for (i <- 0 until num_values)
    {
      if (previous == values.length - 1)
        result += values(previous).value
      else
      {
        var next_position = position(values(next).iteration)
        if (i >= next_position)
        {
          previous = next
          while (i >= next_position && next != values.length - 1)
          {
            previous = next
            next += 1
            next_position = position(values(next).iteration)
          }
          if (i >= next_position)
            previous = next
        }
        val previous_position = position(values(previous).iteration)
        if (previous_position == next_position)
        {
          result += values(previous).value
        }
        else
        {
          val lerp_t = (i.toFloat - previous_position) / (next_position.toFloat - previous_position)
          def lerp(from : Float, to : Float, t : Float) : Float =
            from + (to - from) * t
          result += lerp(values(previous).value, values(next).value, lerp_t)
        }
      }
    }
    result.result
   }
  	def compute_wavelets[T](values : Array[T], iteration : Int, num_levels : Int, method : WaveletFilter)(implicit convert : T => WaveletSource) : Array[Float] =
	{
      val timeseries = build_array(values map convert, iteration, 1 << num_levels)
      StaticWaveletTransform.static_wavelet_transform(timeseries, num_levels, method)
	}
  
  def build_relative_array(values : Array[WaveletSource], iteration : Int, num_values : Int) : Array[Float] =
  {
      val timeseries = build_array(values, iteration, num_values)
      var to_divide = timeseries(0)
      if (to_divide == 0.0f)
      {
        def first_non_zero() : Float =
        {
          for (value <- timeseries)
            if (value != 0.0f)
             return value
          0.0f
        }
        to_divide = first_non_zero
        if (to_divide == 0.0f)
          return timeseries
      }
      
      for (value <- timeseries)
        yield value / to_divide
  }
    // as above but computes relative. meaning in the above the series
    // 10, 20, 30, 40 records a growth by 10 every day
  	// but in this function that series would be recorded as doubled
    // in the first day, then growth by 50%, then growth by 33%
  	def compute_relative_wavelets[T](values : Array[T], iteration : Int, method : WaveletFilter)(implicit convert : T => WaveletSource) : Array[Float] =
	{
  	  val thirty_two_values = build_relative_array(values map convert, iteration, 32)
      StaticWaveletTransform.static_wavelet_transform(thirty_two_values, 5, method)
	}
  	
  	val default_filter : WaveletFilter = Daubechies.SixTap
  	val supported_filters : List[WaveletFilter] = Daubechies.FourTap :: default_filter :: Daubechies.EightTap :: Nil
  
  	def static_wavelet_transform(time_series : Array[Float], num_levels : Int, filter : WaveletFilter) : Array[Float] =
  	{
  	  val result = NewStaticWaveletTransform.static_wavelet_transform(time_series map (_.toDouble), filter, num_levels)
  	  for (level <- result)
  	    yield level.last.toFloat
  	}
  	
  def json_text_to_wavelet_array(text : String) : Array[Array[Float]] =
  {
    def inner_array_to_float_array(array : Array[JValue]) : Array[Float] =
    {
      for (value <- array)
        yield value match
        {
        case JDouble(double) => double.toFloat
        case JInt(int) => int.toFloat
        case _ => throw new Exception("expected int or float")
        }
    }
    
    val a_list = json.parse(text) match
    {
      case JArray(list) => 
        {
          for (JArray(child_array) <- list)
              yield inner_array_to_float_array(child_array.toArray)
        }
      case _ => throw new Exception("expected array")
    }
    a_list.toArray
  }
  
  
}

abstract class WaveletFilter
{
  def quadrature_mirror_filter(scaling : Array[Double]) : Array[Double] =
  {
    val result = scaling.reverse
    for (i <- 0 until result.length / 2)
      result(i * 2 + 1) = -result(i * 2 + 1)
    result
  }
  
  val gt : Array[Double]
  val ht : Array[Double]
  val name : String
}

case class Daubechies(gt : Array[Double], name : String) extends WaveletFilter
{
    val ht = quadrature_mirror_filter(gt)
    val length = gt.length
    
    for (i <- 0 until gt.length)
      gt(i) /= Math.sqrt(2.0)
    for (i <- 0 until ht.length)
      ht(i) /= Math.sqrt(2.0)
}
object Daubechies
{
  def TwoTap() : Daubechies =
    Daubechies(Array(0.7071067811865475, 0.7071067811865475), "d2")
  def FourTap() : Daubechies =
    Daubechies(Array(0.4829629131445341, 0.8365163037378077, 0.2241438680420134, -0.1294095225512603), "d4")
  def SixTap() : Daubechies =
    Daubechies(Array(3.326705529500826159985115891390056300129233992450683597084705e-01,
                     8.068915093110925764944936040887134905192973949948236181650920e-01,
                     4.598775021184915700951519421476167208081101774314923066433867e-01,
                     -1.350110200102545886963899066993744805622198452237811919756862e-01,
                     -8.544127388202666169281916918177331153619763898808662976351748e-02,
                     3.522629188570953660274066471551002932775838791743161039893406e-02), "d6")
  def EightTap() : Daubechies =
    Daubechies(Array(2.303778133088965008632911830440708500016152482483092977910968e-01,
					7.148465705529156470899219552739926037076084010993081758450110e-01,
					6.308807679298589078817163383006152202032229226771951174057473e-01,
					-2.798376941685985421141374718007538541198732022449175284003358e-02,
					-1.870348117190930840795706727890814195845441743745800912057770e-01,
					3.084138183556076362721936253495905017031482172003403341821219e-02,
					3.288301166688519973540751354924438866454194113754971259727278e-02,
					-1.059740178506903210488320852402722918109996490637641983484974e-02), "d8")
}

object NewStaticWaveletTransform
{
  def static_wavelet_transform(values : Array[Double], filter : WaveletFilter, num_levels : Int) : Array[Array[Double]] =
  {
    val result = (for (i <- 0 until num_levels) yield Array[Double]()).toArray
    var input = values
    for (i <- 0 until num_levels)
    {
      val compute = one_iteration(input, i+1, filter)
      result(i) = compute._1
      input = compute._2
    }
    result
  }
  def one_iteration(values : Array[Double], iteration : Int, filter : WaveletFilter) : (Array[Double], Array[Double]) =
  {
    val result = values map (_ => 0.0)
    val new_values = values map (_ => 0.0)
    for (i <- 0 until values.length)
    {
        result(i) = filter.ht(0) * values(i)
        new_values(i) = filter.gt(0) * values(i)
        var k = i
        for (n <- 1 until filter.ht.size)
        {
          k -= 1 << (iteration - 1)
          if (k < 0)
            k = -k
          result(i) += filter.ht(n) * values(k)
          new_values(i) += filter.gt(n) * values(k)
        }
    }
    (result, new_values)
  }
}

class StaticWaveletTransformSpec extends FlatSpec with Matchers
{
  "my old example" should "be the same result as in python" in
  {
    val inputs = Array(4.139, 4.139, 4.151, 4.163, 4.176, 4.189, 4.202, 4.215, 4.227, 4.239, 4.253, 4.266, 4.277, 4.289, 4.301, 4.311, 4.321, 4.327, 4.338, 4.349, 4.359, 4.369, 4.378, 4.385, 4.394, 4.403, 4.412, 4.423, 4.424, 4.434, 4.448, 4.457)
    val result = NewStaticWaveletTransform.static_wavelet_transform(inputs, Daubechies.EightTap, 5)
    result.length should be (5)
    for (array <- result)
      array.length should be (inputs.length)
    result map (_.last) should be (Array(-0.00094213727952840998, -0.0016668663082148871, -0.0050854285850675529, -0.0069588368221449315, -0.0078973618639244236))
  }
  "linear increments" should "be zero in the first value" in
  {
    val values = (for (i <- 0 until 32) yield i.toDouble).toArray
    val result = NewStaticWaveletTransform.static_wavelet_transform(values, Daubechies.SixTap, 5)
    val only_last = result map (_.last)
    println(only_last.mkString(", "))
  }
}

class WaveletParseSpec extends FlatSpec with Matchers
{
    val array_text = """[[0.0, 0.0, 0.5, 0.5], [-0.25, 0.0, 0.25, 0.75]]"""
	import StaticWaveletTransform._
	"valid text" should "be parsed correctly" in
	{
      val arrays = json_text_to_wavelet_array(array_text)
      arrays should be (Array(Array(0.0f, 0.0f, 0.5f, 0.5f), Array(-0.25f, 0.0f, 0.25f, 0.75f)))
	}
}

class WaveletConvertSpec extends FlatSpec with Matchers
{
  import StaticWaveletTransform._
  val just_one_element = Array(WaveletSource(1, 100))
  "the value 100" should "be converted to 100, 100, 100, 100" in
  {
    val converted = build_array(just_one_element, 4, 4)
    converted should be (Array(100, 100, 100, 100))
  }
  val missing_one_array = Array(WaveletSource(1, 10), WaveletSource(2, 20), WaveletSource(4, 40))
  "the values 10, 20, _, 40" should "be converted to 10, 20, 30, 40" in
  {
    val converted = build_array(missing_one_array, 4, 4)
    converted should be (Array(10, 20, 30, 40))
  }
  val missing_two_array = Array(WaveletSource(1, 10), WaveletSource(4, 40))
  "the values 10, _, _, 40" should "be converted to 10, 20, 30, 40" in
  {
    val converted = build_array(missing_two_array, 4, 4)
    converted should be (Array(10, 20, 30, 40))
  }
  val missing_at_end = Array(WaveletSource(1, 10), WaveletSource(2, 20))
  "the values 10, 20, _, _" should "be converted to 10, 20, 20, 20" in
  {
    val converted = build_array(missing_at_end, 4, 4)
    converted should be (Array(10, 20, 20, 20))
  }
  val array = Array(WaveletSource(1, 10), WaveletSource(2, 20), WaveletSource(3, 30), WaveletSource(4, 40))
  "the values 10, 20, 30, 40" should "be converted to 10, 20, 30, 40" in
  {
    val converted = build_array(array, 4, 4)
    converted should be (Array(10, 20, 30, 40))
  }
  "the values 10, 20, 30, 40" should "be converted to 1, 2, 3, 4" in
  {
    val converted = build_relative_array(array, 4, 4)
    converted should be (Array(1, 2, 3, 4))
  }
  "the values 10, 20, 30, 40" should "be converted to 1, 1, 1, 1, 1, 2, 3, 4" in
  {
    val converted = build_relative_array(array, 4, 8)
    converted should be (Array(1, 1, 1, 1, 1, 2, 3, 4))
  }
  "the values 5, 20, 33, 40, 50" should "be converted to 40, 50" in
  {
    val array = Array(WaveletSource(1, 5), WaveletSource(2, 20), WaveletSource(3, 33), WaveletSource(4, 40), WaveletSource(5, 50))
    val converted = build_array(array, 5, 2)
    converted should be (Array(40, 50))
  }
  "an array with zeros" should "not contain infinity when relative" in
  {
    val array = Array(WaveletSource(1, 0.0f), WaveletSource(2, 0.0f), WaveletSource(3, 0.0f), WaveletSource(4, 0.0f), WaveletSource(5, 0.0f), WaveletSource(6, 29524.0f), WaveletSource(7, 29546.0f), WaveletSource(8, 29595.0f), WaveletSource(9, 29631.0f), WaveletSource(10, 29670.0f), WaveletSource(11, 29712.0f), WaveletSource(12, 29738.0f), WaveletSource(13, 29772.0f), WaveletSource(14, 29801.0f), WaveletSource(15, 29815.0f), WaveletSource(16, 29849.0f), WaveletSource(17, 29873.0f), WaveletSource(18, 29903.0f), WaveletSource(19, 29939.0f), WaveletSource(20, 29971.0f), WaveletSource(21, 30105.0f), WaveletSource(22, 30178.0f), WaveletSource(23, 30228.0f), WaveletSource(24, 30254.0f), WaveletSource(25, 30312.0f), WaveletSource(26, 30361.0f), WaveletSource(27, 30385.0f), WaveletSource(28, 30403.0f), WaveletSource(29, 30425.0f), WaveletSource(30, 30462.0f), WaveletSource(31, 30481.0f), WaveletSource(32, 30521.0f))
    val converted = build_relative_array(array, 32, 32)
    converted should not contain(Float.PositiveInfinity)
  }
  "asking for more values" should "not just make up values" in
  {
    val array = Array(WaveletSource(18661, 1135618), WaveletSource(18662, 1139684), WaveletSource(18663, 1144308), WaveletSource(18664, 1149072), WaveletSource(18665, 1153982), WaveletSource(18666, 1159171), WaveletSource(18667, 1163742))
    val converted = build_array(array, 18687, 6)
    converted should be (Array(1163742, 1163742, 1163742, 1163742, 1163742, 1163742))
  }
}

