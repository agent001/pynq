package pynq

import chisel3._
import chisel3.util._

class AxiLiteFollower extends Bundle {
	val data_bits = 32
	val address_bits = 4
	val words = (1 << address_bits)
	// TODO: maybe use ReadyValid interface from stdlib
	// read address interface
	val araddr  = Input(UInt(address_bits.W))
	val arready = Output(Bool())
	val arvalid = Input(Bool())
	// write address interface
	val awaddr  = Input(UInt(address_bits.W))
	val awready = Output(Bool())
	val awvalid = Input(Bool())
	// write response interface
	val bresp  = Output(UInt(2.W))
	val bready = Input(Bool())
	val bvalid = Output(Bool())
	// read data interface
	val rdata = Output(UInt(data_bits.W))
	val rresp = Output(UInt(2.W))
	val rready = Input(Bool())
	val rvalid = Output(Bool())
	// write data interface
	val wdata = Input(UInt(data_bits.W))
	val wready = Output(Bool())
	val wvalid = Input(Bool())
}

// This is the simplest AxiLite Follower I could think of.
// It does not accept writes and always returns a single constant, no matter what
// address is read from.
class AxiLiteReadOneConstant extends Module {
	val io = IO(new AxiLiteFollower)

	val magic = 0x12345678

	val OK = 0.U

	// read
	io.arready := true.B
	io.rdata := magic.U
	io.rresp := OK
	io.rvalid := true.B

	// write
	io.awready := false.B
	io.bresp := 0.U // does not matter
	io.bvalid := false.B
	io.wready := false.B

}

// This extends the above example to provide a different constant for
// each address.
class AxiLiteReadDifferentConstants extends Module {
	val io = IO(new AxiLiteFollower)

	val data = Seq(0x11111111, 0x2222222, 0x3333333, 0x4444444, 0x5555555,
	               0x66666666, 0x7777777, 0x8888888, 0x9999999, 0xaaaaaaa)
	val default = 0

	val OK = 0.U

	// read state
	val sWaitForAddress :: sSendData :: Nil = Enum(2)
	val read_state = RegInit(sWaitForAddress)
	switch(read_state) {
		is(sWaitForAddress) { when(io.arready && io.arvalid) { read_state := sSendData } }
		is(sSendData) { when(io.rready && io.rvalid) { read_state := sWaitForAddress } }
	}

	// read
	val read_address = RegInit(0.U(32.W))
	when(io.arready && io.arvalid) { read_address := io.araddr }
	io.arready := read_state === sWaitForAddress
	io.rdata := MuxLookup(read_address, default.U, data.zipWithIndex.map{ case(d, ii) => ii.U -> d.U })
	io.rresp := OK
	io.rvalid := read_state === sSendData

	// write
	io.awready := false.B
	io.bresp := 0.U // does not matter
	io.bvalid := false.B
	io.wready := false.B

}

object AxiLiteHelloWorldGenerator extends App {
	chisel3.Driver.execute(args, () => new AxiLiteReadOneConstant)
	chisel3.Driver.execute(args, () => new AxiLiteReadDifferentConstants)
}