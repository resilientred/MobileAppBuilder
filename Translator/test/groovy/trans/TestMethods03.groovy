
def m1(int i, int j = 0) { 
  i + j
}

def m1(List l, int i = 0) { 
  l << i 
}

println m1(100, 1)
println m1(100)

println m1([1, 2, 3], 100)
println m1([1, 2, 3])