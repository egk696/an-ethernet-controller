# A lightweight Ethernet MAC Controller

## Features
* Modular design, separate entities for the MII interfaces and the RX, TX and PHY management controllers 
* Simple OCP bus interface for host control
* Easy FIFO frame buffer control that is automatically managed in hardware
* Time-stamping of received Ethernet frames
  
### Example Usage
When the _SFD_ is detected by the MII interface, the RX controller starts to write the frame in the selected frame buffer. When the _EOF_ is detected it increments the _wrRamSelId_ pointer and it selects the next frame buffer to write. If the FIFO is not empty the CPU can start reading a frame, after it has completed reading the selected Ethernet frame it writes a register that increments the _rdRamSelId_ pointer to the next available frame buffer. The CPU can immediately start reading the next received frame without by offloading any buffer management to the hardware controller.
