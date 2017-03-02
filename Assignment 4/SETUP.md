[Go Back][1]
# Network setup guidelines
### Required downloads
- GNS3
- Micro core image
- Cisco c3700 router
- GNS3 setup

Download and install GNS3. Run the application, go to *Edit->IOS images and hypervisors* and select the downloaded *Cisco c3700 image* in the Image file field. Do not auto-calculate Idle PC. Now you should be able to drag a c3700 router from the list into the workspace. Run the dragged R1 router (green triangle in the top menu), right-click it and select Console. Wait until the router boots up and type enable. You should see R1# in the console. Now right-click R1 and select Idle PC. Make sure you have closed all programs before doing that and do not perform any operations on your computer while calculation is in progress. Once the calculation is complete, pick one of the values marked with asterisk (*). You should then observe a decrease in CPU usage. You can add routers R2-R4.

Go to *Edit->Preferences->Qemu->Qemu Guest*. Put an identifier (e.g., PC) in the according field and select the downloaded Micro core image in the Binary image field. Press Save and OK. Now you should be able to drag a Qemu guest machine from the list of PCs into the workspace. Hence, you can add computers C1-C2. 

You are ready to build up a network topology.

## Network topology
![Network topology](http://i.imgur.com/dOvdm1q.jpg "topology")


You are expected to build a topology displayed on the figure above. As you can observe, there exists three paths from C1 to C2. These are R1->R2->R4, R1->R4 and R1->R3->R4.

By default, each router has only two interfaces available for connection.To add more interfaces: right click a router, then Configure, select the router node, go to Slots and add a NM-1FE-TX module. For connection between R3 and R4 select WIC-1T in the wic 0 slot. Finally, Select View->Show/Hide interface labels to display the names of the router interfaces.

After creating all routes according to the figure, you can start the system (green triangle in the top menu). Login for Qemu machines is tc. Use sudo su command to switch to root. It is time to configure the network nodes.

## Network configuration

Each router interface (red dot in the topology) and Qemu node (green dots) will have its own IP address. Your task is to create a correct IP addressing scheme with only one requirement: you must have */30* subnets in the backbone and */24* subnets in networks A and B. **Hint**: use a subnet mask [cheat sheet](https://www.aelius.com/njh/subnet_sheet.html). **Hint 2**: first, create this scheme on paper or by editing a screenshot of topology in a graphical editor.

When the IP scheme is ready, configure the routers using the following console commands:

> conf t

> int [router_interface] //For example, f0/0

> ip address [your_ip] [your_mask]

> bandwidth [your_bandwidth] //Find out what metric is used for bandwidth

> no shutdown

> end

After that you should configure the Qemu nodes (X in ethX should be replaced with a number of used Qemu interface, e.g. eth0 for interface e0):

ifconfig ethX [your_ip] netmask [your_mask] up 
route add default gw [ip] ethX //find out the meaning of this command to insert a correct [ip] address
If you face an error message about duplex mismatch, you may enter the following in the router config:

> conf t

> no cdp log mismatch duplex

> end

After configuration is done, try to ping the next hop from each of the network nodes (only the next hop, not from R2 to R3 or C1 to R3). Once the pings are successful, your network topology is ready for the assignment.

## Saving/Loading a configuration

At the beginning of each problem of this assignment, you will need to return to the basic state of configuration on each router (not required for computers C1 and C2). Therefore, you need to first save the configuration of each router by issuing the wr mem console command. Then, in GNS3 click *File->Import/Export* and export all configurations to a folder at your PC. Mind that rebooting GNS3 will erase the settings of computers C1 and C2. When you need to return to the basic configuration, stop all of your router instances, click *File->Import/Export* and import a previously saved configuration to the routers.

**Hint**: all router configurations can be saved in a *.txt* document and simply copied in the router console with shift+insert. 
[Go Back][1]

[1]: README.md