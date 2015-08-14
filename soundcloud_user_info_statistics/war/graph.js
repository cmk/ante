function linear_graph(data, id, width, height)
{
	var margin = {top: 20, right: 30, bottom: 30, left: 60};
	width = width - margin.left - margin.right,
	height = height - margin.top - margin.bottom;
	
	var x = d3.scale.linear()
	    .domain([0, data.length])
	    .range([0, width]);
	var y = d3.scale.linear()
	    .domain([d3.min(data), d3.max(data)])
	    .range([height, 0]);
	
	var chart = d3.select("#" + id)
	    .attr("width", width + margin.left + margin.right)
	    .attr("height", height + margin.top + margin.bottom)
	  .append("g")
		.attr("class", "generated_chart")
		.attr("transform", "translate(" + margin.left + ", " + margin.top + ")");
	
	var xAxis = d3.svg.axis()
	    .scale(x)
	    .orient("bottom");
	
	var yAxis = d3.svg.axis()
	    .scale(y)
	    .orient("left");
	
	chart.append("g")
	    .attr("class", "x axis")
	    .attr("transform", "translate(0," + height + ")")
	    .call(xAxis);
	
	chart.append("g")
	    .attr("class", "y axis")
	    .call(yAxis)
	  .append("text")
		.attr("y", -3)
	    .text("playcount");

    var lineFunction = d3.svg.line()
                          .x(function(d, i) { return x(i); })
                          .y(function(d) { return y(d); })
                          .interpolate("linear");
      
    var lineGraph = chart.append("path")
                            .attr("d", lineFunction(data));
}


function compact_linear_graph(data, id, width, height, dotted_index)
{
	var margin = { top : 0, right : 75, bottom : 0, left : 75 };
	width = width - margin.left - margin.right,
	height = height - margin.top - margin.bottom;
	
	var x = d3.scale.linear()
	    .domain([0, data.length - 1])
	    .range([0, width]);
	var min_value = d3.min(data)
	var max_value = d3.max(data)
	var y = d3.scale.linear()
	    .domain([min_value, max_value])
	    .range([height, 0]);
	
	var chart = d3.select("#" + id)
	    .attr("width", width + margin.left + margin.right)
	    .attr("height", height + margin.top + margin.bottom)
	  .append("g")
		.attr("class", "generated_chart")
		.attr("transform", "translate(" + margin.left + ", " + margin.top + ")");

	/*chart.append("rect")
	    .attr("class", "x axis")
	    .attr("transform", "translate(0," + (height - 1) + ")")
	    .attr("width", width)
	    .attr("height", 1);*/
	
	chart.append("text")
		.attr("x", width / 2)
		.attr("y", height - 10)
		.text(Math.round((data.length * 7) / 8) + " days")
		.style("text-anchor", "middle");
	
	/*chart.append("text")
		.attr("x", 0)
		.attr("y", 0)
	    .text("playcount")
	    .style("dominant-baseline", "hanging");*/
	
	var format = d3.format(",g")
	
	chart.append("text")
		.attr("x", x(0))
		.attr("y", y(min_value) - 2)
		.text(format(min_value))
		.style("text-anchor", "end");
	
	chart.append("text")
		.attr("x", x(data.length - 1))
		.attr("y", y(max_value))
		.text(format(max_value))
		.style("text-anchor", "start")
		.style("dominant-baseline", "hanging");

    var lineFunction = d3.svg.line()
                          .x(function(d, i) { return x(i); })
                          .y(function(d) { return y(d); })
                          .interpolate("linear");
      
    var lineGraph = chart.append("path")
                            .attr("d", lineFunction(data));
    
    if (dotted_index != null)
    {
	    var dot_size = 4
	    var dot = chart.selectAll("rect")
	      .data([dotted_index])
	        .enter().append("rect")
	        .attr("transform", function(d)
	        {
	  	      return "translate(" + (x(d) - dot_size / 2) + ", " + (y(data[d]) - dot_size / 2) + ")";
	        })
	        .attr("width", dot_size)
		    .attr("height", dot_size);
    }
	/*var bar = chart.selectAll("rect")
	    .data(data)
	  .enter().append("rect")
	    .attr("transform", function(d, i)
        {
      	  return "translate(" + x(i) + ", " + y(d) + ")";
        })
        .attr("width", 3)
		.attr("height", 3);*/
}
