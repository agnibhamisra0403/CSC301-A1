#!/bin/bash

if [ "$1" == "-c" ]; then
    # compiling the code entirely
    
    # create a subdirectory to store the compiled code
    mkdir -p a1/compiled/UserService a1/compiled/ProductService a1/compiled/OrderService a1/compiled/ISCS

    # compile the code - helpers first
    javac -cp a1/compiled -d a1/compiled a1/src/UserService/UserService.java
    javac -cp a1/compiled -d a1/compiled a1/src/ProductService/ProductService.java
    javac -cp a1/compiled -d a1/compiled a1/src/OrderService/OrderService.java
    javac -cp a1/compiled -d a1/compiled a1/src/OrderService/WorkloadParser.java
    javac -cp a1/compiled -d a1/compiled a1/src/ISCS/ISCS.java

    # copy config file into compiled directory
    cp a1/config.json a1/compiled/

elif [ "$1" == "-u" ]; then
    # start the user service
    java -cp a1/compiled UserService.UserService a1/config.json
elif [ "$1" == "-p" ]; then
    # start the Product service
    java -cp a1/compiled ProductService.ProductService a1/config.json
elif [ "$1" == "-i" ]; then
    # start the ISCS
    java -cp a1/compiled ISCS.ISCS a1/config.json
elif [ "$1" == "-o" ]; then
    # start the Order service
    java -cp a1/compiled OrderService.OrderService a1/config.json
elif [ "$1" == "-w" ]; then
    # start the workload parser
    # $2 is the workload file path given after the -w flag
    if [ -z "$2" ]; then
        echo "Error: provide a workload file path"
        exit 1
    fi
    java -cp a1/compiled OrderService.WorkloadParser "$2"

else
    echo "Usage: ./runme.sh {-c|-u|-p|-i|-o|-w workloadfile}"
    exit 1
fi