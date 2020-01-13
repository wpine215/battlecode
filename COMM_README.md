# Stardust Communication Protocol (SCP)

### Messages from HQ

- Genesis Message (high priority, sent 1st round)
  - 1) Hashed round number (upper 7 digits), identifier (lower 3 digits (**101**))
  - 2) Home HQ Coordinates (lower 4 digits)
- Rebroadcast Message (high priority, sent every 10 rounds)
  - 1) Hashed round number (upper 7 digits), identifier (lower 3 digits (**102**))
  - 2) Soup Deposit Location #1 (upper 4 digits), Soup Deposit Location #2 (lower 4 digits)
  - 3) Soup Deposit Location #3 (upper 4 digits), Soup Deposit Location #4 (lower 4 digits)
  - 4) Refinery Location #1 (upper 4 digits), Refinery Location #2 (lower 4 digits)
  - 5) Home HQ Coordinates (upper 4 digits), Enemy HQ Coordinates (lower 4 digits)
  - 6) Home HQ Health (upper 2 digits), HQ health change/delta since last rebroadcast (lower 2 digits, with the sign (+/-) indicating whether change is positive or negative)
  - 7) Reserved for future use (enemy design school locations? water locations?)
- Enemies in proximity to HQ (high priority, sent every 10 rounds offset by 5)
  - 1) Hashed round number (upper 7 digits), identifier (lower 3 digits (**103**))
  - 2) 
- Build Refinery
  - 1) Hashed round number (upper 7 digits), identifier (lower 3 digits (**120**))
  - 2) Coordinates to build refinery at (lower 4 digits)
  - 3) Approximate # of miners to allocate task to (using RNG)
- Build Design School
  - 1) Hashed round number (upper 7 digits), identifier (lower 3 digits (**130**))
  - 2) Coordinates to build design school at (lower 4 digits)
  - 3) Approximate # of miners to allocate task to (using RNG)
- Build Drone Factory/Fullfilment center
  - 1) Hashed round number (upper 7 digits), identifier (lower 3 digits (**140**))
  - 2) Coordinates to build drone factory at (lower 4 digits)
  - 3) Approximate # of miners to allocate task to (using RNG)

### Messages from Miners

- Soup Deposit Located
  - 1) Hashed round number (upper 7 digits), identifier (lower 3 digits (**201**))
  - 2) Coordinates of soup deposit (lower 4 digits)
- Soup Deposit Area Depleted
  - 1) Hashed round number (upper 7 digits), identifier (lower 3 digits (**202**))
  - 2) Coordinates of soup deposit (lower 4 digits)
- Enemy HQ Located
  - 1) Hashed round number (upper 7 digits), identifier (lower 3 digits (**220**))
  - 2) Coordinates of enemy HQ (lower 4 digits)
- Enemy Design School Located
  - 1) Hashed round number (upper 7 digits), identifier (lower 3 digits (**221**))
  - 2) Coordinates of enemy design school (lower 4 digits)

### Messages from Landscapers





