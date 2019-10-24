#!/bin/sh

if [ $# -lt 1 -o $# -gt 3 ]; then
  echo "usage: ./test.sh [variable limit size] [sample size] [-TX]"
  exit -1
fi
size=$1
loop=$2
if [ $# -lt 3 ]; then
  arg="-T1"
else
  arg=$3
fi
echo "#BEGIN" > out.txt
count=1
while [ $count -le $size ]
do
  echo "variable size $count begin..."
  cd ./vmdl_test/vmdgen
  echo "ins.vmd generate begin..."
  java Main $count > ../ins.vmd
  echo "done."
  cd ../..
  echo "&$count" >> out.txt
  echo "vmdlc begin..."
  count2=1
  while [ $count2 -le $loop ]
  do
    echo "$count2"
    time java -jar vmdlc.jar -d vmdl_test/default.def -i vmdl_test/insins.def -o vmdl_test/ins.spec $arg vmdl_test/ins.vmd > out_temp 2>> out.txt
    count2=`expr $count2 + 1`
  done
  echo "done."
  count=`expr $count + 1`
done
cd ./vmdl_test/avgcalc
java Main ../../out.txt
