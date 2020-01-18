# Stardust Communication Protocol (SCP)

### Messages from HQ

- Genesis Message (high priority, sent 1st round)
  - 1) Hashed round number (upper 7 digits), identifier (lower 3 digits (**101**))
  - 2) Home HQ Coordinates (lower 4 digits)
- Rebroadcast Message (high priority, sent every 10 rounds offset by 5)
  - 1) Hashed round number (upper 7 digits), identifier (lower 3 digits (**102**))
  - 2) Soup Deposit #1 (upper 2 digits), Soup Deposit #2 (upper middle 2 digits), Soup Deposit #3 (upper lower 2 digits), Soup Deposit #4 (lower 2 digits)
  - 3) Soup Deposit #5 (upper 2 digits), Soup Deposit #6 (upper middle 2 digits), Soup Deposit #7 (upper lower 2 digits), Soup Deposit #8 (lower 2 digits)
  - 4) Refinery Location #1 (upper 4 digits), Refinery Location #2 (lower 4 digits)
  - 5) Refinery Location #3 (upper 4 digits), Refinery Location #4 (lower 4 digits)
  - 6) Enemy HQ Coordinates (4 digits)
  - 7) Home HQ Health (2 digits)
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
- Built Refinery
  - 1) Hashed round number (upper 7 digits), identifier (lower 3 digits (**230**))
  - 2) Coordinates of refinery (lower 4 digits)

### Messages from Landscapers





