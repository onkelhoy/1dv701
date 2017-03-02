# Assignment 4

*The setup info*
[SETUP INSTRUCTIONS](SETUP.md)

### Tasks
- [ ] Problem 1

Configure a network virtual environment and create a required topology according to the guidelines. You report should include:

- A resulting screenshot from GNS3 with a completed topology that contains all IP addresses (can be added with Notes tool).

- Screenshots of pings from R1 to C1 and R1 to R4.

- An explanation of NM-1FE-TX and WIC-1T abbreviations and why these modules are chosen among the available alternatives.

- Answer to the following question: what is the practical difference between a /24 and a /30 subnet? 

- [ ] Problem 2

In this problem you will work with static routing. Choose one of the three available routing passes between C1 and C2 that is more efficient in your opinion. Then implement static routing using this pass. To add one route to a router, use the following commands:

enable
conf t
ip route [ip] [mask] [router_interface] [metric]
end
Explain each of the parameters of the ip route command in your report. When a complete route between Qemu nodes is ready, send ping commands from C1 to C2 in order to test the connection. Include resulting screenshot and explain the choice of a routing pass in your report.

Start a continuous ping from C1 to C2. Then shut down one of the active router interfaces along the path connecting C1 and C2:

conf t
int [router_interface]
shut
Observe that pings are failing. Your task now is to configure the remaining two routing passes between C1 and C2. Once finished, start a continuous ping from C1 to C2 again and shut down one of the active router interfaces. Observe how many packets are lost before a new routing pass starts working and include a screenshot in your report. Hint: how do you know that a routing pass changed?

VG-task 1: Add a new /24 network to R4. To do that, use a loopback interface. Ping the new network from C1 and include a resulting screenshots in your report. Repeat this scenario in problem 3, include a screenshot.

- [ ] Problem 3

Return to the basic router configuration. Now configure a dynamic routing protocol RIPv2 in all routers using the following commands:

enable
conf t
router rip
version 2
no auto-summary
network [ip]
network [ip2]
network ...
end
Read about configuring RIPv2 routers in order to specify the correct amount of networks with appropriate IP addresses for each router. Use a traceroute command from C1 to C2 to see which routing path has been picked by RIPv2 as the most efficient. Explain this choice in your report.

Similar to Problem 2, start a continuous ping between C1 and C2 and shut down one of the active router interfaces. Observe how many packets are lost before a new routing pass starts working and include a screenshot in your report. Using traceroute, observe which path is chosen by RIPv2 in case of failure and explain it in your report.

- [ ] Problem 4

Return to the basic router configuration. Now configure a dynamic routing protocol OSPF in all routers using the following commands:

enable
conf t
router ospf 1
network [ip] [wildcard_mask] area [area_num]
network [ip2] [wildcard_mask2] area [area_num]
network ...
end
Read about configuring OSPF routers in order to specify the correct amount of networks with appropriate IP addresses, masks and areas for each router. Repeat all testing scenarios from Problem 3 and include explanations/screenshots in your report.

When finished, write a concluding section in your report, where you explain the key differences between the three routing methods (static/RIPv2/OSPF) and give a few scenarios where each of these methods would be the most efficient to use. Hint: take into account the configuration time and amount of packets lost in case of failure.

VG-task 2: return to the basic router configuration. Configure a dynamic routing protocol EIGRP in all routers. As this is a VG task, you will have to find a proper configuration commands on your own. Use a traceroute command from C1 to C2 to see which routing path has been picked by EIGRP as the most efficient. Explain this choice and add a comparison of other routing methods with EIGRP to the concluding section of your report (including scenarios where EIGRP may be preferred over other methods). Hint: you might want to repeat the scenario with a route shutdown during a continuous ping to provide evidence supporting the use of EIGRP.

Submission

In this assignment you only submit a report. For other information read the general submission instructions.