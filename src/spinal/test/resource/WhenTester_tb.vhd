library IEEE;
use IEEE.STD_LOGIC_1164.ALL;
use IEEE.NUMERIC_STD.all;

library work;
use work.pkg_scala2hdl.all;
use work.pkg_enum.all;

-- #spinalBegin userLibrary
library IEEE;
use ieee.math_real.all;
-- #spinalEnd userLibrary


entity WhenTester_tb is
end WhenTester_tb;

architecture arch of WhenTester_tb is
  signal io_conds_0 : std_logic;
  signal io_conds_1 : std_logic;
  signal io_conds_2 : std_logic;
  signal io_conds_3 : std_logic;
  signal io_conds_4 : std_logic;
  signal io_conds_5 : std_logic;
  signal io_conds_6 : std_logic;
  signal io_conds_7 : std_logic;
  signal io_data_0 : unsigned(7 downto 0);
  signal io_data_1 : unsigned(7 downto 0);
  signal io_data_2 : unsigned(7 downto 0);
  signal io_data_3 : unsigned(7 downto 0);
  signal io_data_4 : unsigned(7 downto 0);
  signal io_data_5 : unsigned(7 downto 0);
  signal io_data_6 : unsigned(7 downto 0);
  signal io_data_7 : unsigned(7 downto 0);
  signal io_data_8 : unsigned(7 downto 0);
  signal io_data_9 : unsigned(7 downto 0);
  signal io_data_10 : unsigned(7 downto 0);
  signal io_data_11 : unsigned(7 downto 0);
  signal io_outDefault : unsigned(7 downto 0);
  signal io_outComplex : unsigned(7 downto 0);
  -- #spinalBegin userDeclarations
  signal io_outDefault_ref : unsigned(7 downto 0);
  signal io_outComplex_ref : unsigned(7 downto 0);
  -- #spinalEnd userDeclarations
begin
  -- #spinalBegin userLogics

  process
    variable seed1, seed2: positive;              
    variable testCounter : integer := 0;
    procedure random(signal that: out std_logic) is 
      variable rand: real;                          
      variable int_rand: integer;                    
      variable vector: std_logic_vector(11 DOWNTO 0);  
    begin
      UNIFORM(seed1, seed2, rand);   
      int_rand := INTEGER(TRUNC(rand*4096.0));                       
      vector := std_logic_vector(to_unsigned(int_rand, vector'LENGTH));  
      that <= vector(3);
    end random;
    
     procedure random(signal that: out unsigned) is 
      variable rand: real;                          
      variable int_rand: integer;                     
    begin
      UNIFORM(seed1, seed2, rand);   
      int_rand := INTEGER(TRUNC(rand*(2.0**that'length)));                       
      that <= (to_unsigned(int_rand, that'LENGTH));  
    end random;
  begin
    random(io_conds_0);
    random(io_conds_1);
    random(io_conds_2);
    random(io_conds_3);
    random(io_conds_4);
    random(io_conds_5);
    random(io_conds_6);
    random(io_conds_7);
    random(io_data_0);
    random(io_data_1);
    random(io_data_2);
    random(io_data_3);
    random(io_data_4);
    random(io_data_5);
    random(io_data_6);
    random(io_data_7);
    random(io_data_8);
    random(io_data_9);
    random(io_data_10);
    random(io_data_11);
    wait for 1 ns;
    if io_conds_0 = '1' then
      io_outDefault_ref <= io_data_1;
    else
      io_outDefault_ref <= io_data_0;
    end if;

    if io_conds_0 = '1' then
      io_outComplex_ref <= io_data_0;
    elsif io_conds_1 = '1' then
      io_outComplex_ref <= io_data_1;
      if io_data_3 = io_data_4 then
          io_outComplex_ref <= io_data_5;
      end if;
      if io_data_3 = io_data_6 then
        io_outComplex_ref <= io_data_7;
      end if;
      if io_data_3 = X"55" then
        if io_conds_2 = '1' then
          io_outComplex_ref <= X"AA";
        elsif io_conds_3 = '1' then
          io_outComplex_ref <= io_data_8;
        end if;
      end if;
    else
      if io_conds_4 = '1' then
        io_outComplex_ref <= io_data_9;
      else
        io_outComplex_ref <= io_data_10;
      end if;
    end if;
    wait for 9 ns;

    assert io_outDefault = io_outDefault_ref report "io_outDefault fail" severity failure;
    assert io_outComplex = io_outComplex_ref report "io_outComplex fail" severity failure;


    testCounter := testCounter + 1;
    if testCounter = 10000 then
      wait;
    end if;
    
  end process;
                                  
    
  -- #spinalEnd userLogics
  uut : entity work.WhenTester
    port map (
      io_conds_0 =>  io_conds_0,
      io_conds_1 =>  io_conds_1,
      io_conds_2 =>  io_conds_2,
      io_conds_3 =>  io_conds_3,
      io_conds_4 =>  io_conds_4,
      io_conds_5 =>  io_conds_5,
      io_conds_6 =>  io_conds_6,
      io_conds_7 =>  io_conds_7,
      io_data_0 =>  io_data_0,
      io_data_1 =>  io_data_1,
      io_data_2 =>  io_data_2,
      io_data_3 =>  io_data_3,
      io_data_4 =>  io_data_4,
      io_data_5 =>  io_data_5,
      io_data_6 =>  io_data_6,
      io_data_7 =>  io_data_7,
      io_data_8 =>  io_data_8,
      io_data_9 =>  io_data_9,
      io_data_10 =>  io_data_10,
      io_data_11 =>  io_data_11,
      io_outDefault =>  io_outDefault,
      io_outComplex =>  io_outComplex 
    );
end arch;
