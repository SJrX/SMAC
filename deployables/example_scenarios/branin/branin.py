#!/usr/bin/python
import sys, math, random

try:

    args = sys.argv

    # Convert arguments after --config which alternate between specify key and value into a map.
    params = { args[i]:args[i+1]  for i in range( args.index("--config")+1, len(args), 2) }

    x1 = float(params['-x1'])
    x2 = float(params['-x2'])

    # Compute the branin function:
    yValue = (x2 - (5.1 / (4 * math.pi * math.pi)) *x1*x1 + (5 / (math.pi)) *x1 -6) ** 2 + 10*(1- (1 / (8 * math.pi))) * math.cos(x1) + 10

    # SMAC has a few different output fields; here, we only need the 4th output:
    print ('Result of this algorithm run: { "status":"SUCCESS", "cost":%f }' % yValue)

except (Exception) as e:

    #Aside from status you can output other keys if you'd like
    #Just make sure when making your own JSON that it doesn't include quotes or newlines.
    print ('Result of this algorithm run: { "status":"ABORT", "error":"%s"} '% e)
