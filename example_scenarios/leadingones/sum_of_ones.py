#!/usr/bin/python
import sys, math, random

# For black box function optimization, we can ignore the first 5 arguments. 
# The remaining arguments specify parameters using this format: -name value 

n_params = (len(sys.argv) - 5) /2 

nums = [0]*n_params 
for id_, value in enumerate(sys.argv[6:]):
  if id_ % 2 == 0:
    continue
  nums[ int(sys.argv[6+id_-1][2:]) -1 ] = int(value)

count = 0
for v in nums:
  if v == 1:
    count +=1

  
# SMAC has a few different output fields; here, we only need the 4th output:
print("Result of algorithm run: SUCCESS, 0, 0, %f, 0") % -count
 
