library IEEE;
use IEEE.STD_LOGIC_1164.ALL;
use IEEE.NUMERIC_STD.all;

library work;
use work.pkg_scala2hdl.all;
use work.pkg_enum.all;

-- #spinalBegin userLibrary
-- #spinalEnd userLibrary


entity MultiClockTester_tb is
end MultiClockTester_tb;

architecture arch of MultiClockTester_tb is
  signal io_clkA : std_logic;
  signal io_resetA : std_logic;
  signal io_clkB : std_logic;
  signal io_resetB : std_logic;
  signal io_slave0_valid : std_logic;
  signal io_slave0_ready : std_logic;
  signal io_slave0_data_a : unsigned(7 downto 0);
  signal io_slave0_data_b : std_logic;
  signal io_master0_valid : std_logic;
  signal io_master0_ready : std_logic;
  signal io_master0_data_a : unsigned(7 downto 0);
  signal io_master0_data_b : std_logic;
  signal io_fifo0_pushOccupancy : unsigned(4 downto 0);
  signal io_fifo0_popOccupancy : unsigned(4 downto 0);
  -- #spinalBegin userDeclarations
  -- #spinalEnd userDeclarations
begin
  -- #spinalBegin userLogics
  -- #spinalEnd userLogics
  uut : entity work.MultiClockTester
    port map (
      io_clkA =>  io_clkA,
      io_resetA =>  io_resetA,
      io_clkB =>  io_clkB,
      io_resetB =>  io_resetB,
      io_slave0_valid =>  io_slave0_valid,
      io_slave0_ready =>  io_slave0_ready,
      io_slave0_data_a =>  io_slave0_data_a,
      io_slave0_data_b =>  io_slave0_data_b,
      io_master0_valid =>  io_master0_valid,
      io_master0_ready =>  io_master0_ready,
      io_master0_data_a =>  io_master0_data_a,
      io_master0_data_b =>  io_master0_data_b,
      io_fifo0_pushOccupancy =>  io_fifo0_pushOccupancy,
      io_fifo0_popOccupancy =>  io_fifo0_popOccupancy 
    );
end arch;
