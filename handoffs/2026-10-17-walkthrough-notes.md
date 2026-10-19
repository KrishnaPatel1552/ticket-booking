# Customer walkthrough notes

Date: Saturday 17 October 2026, evening ET.
Site: http://127.0.0.1:5173/ on `stitch-customer-site`.
Accounts used: `ada@example.com`, then `grace@example.com`. Password `password`.

## Passed

- A wrong password stays on the sign-in card and shows "That email and password did not sign you in."
- A correct password opens the events screen. The search label reads "Search events by name."
- An empty search shows "Enter an event name."
- `zzzz` shows "No events match that name."
- `Harbor` lists Harbor Lights, Harbor Hall, and Open seat map.
- The seat map opens. With nothing selected it says "Select free seats to hold."
- A1 and A2 were already tickets, so they could not be selected. A6 and A7 selected, the total was $80.00, and Hold seats enabled.

## Open

### 1. A customer cannot change a hold

Noted while Grace held A6 and A7 until 5:46 PM.

The Hold screen has Change seats. That button only returns to the seat map. It does not release A6 and A7. There is no release control. The same seats cannot be held again, so the customer cannot replace the selection. The seats stay held until 5:46 PM, and they become free the next time the seat map is opened after that. A declined checkout also releases them.

Ada had already hit the same wall earlier: 7 tickets and 5 holds, 12 in all. Hold seats on two free seats answered "Those seats could not be held." The page does not say the customer is at the limit of 12.

### 2. A held seat has no reliable way to checkout

Noted on the same hold.

The order screen exists only in the browser page that just made the hold. Check out on that page opens a screen whose only payment step is a scripted result: success, decline, timeout, or unknown. Choosing nothing and clicking Check out does nothing.

Seats that are already held on the map have no Check out. Ada's earlier holds (A3, A4, A10, B3, B4) are in that state. Leaving the Hold screen, or reloading, drops the order from the page while the seats stay held, and there is no way back to pay for them.

Confirmed from the seat map: there is no button that opens checkout for seats already marked held. The only Check out control is on the Hold screen that appears immediately after Hold seats succeeds. Change seats leaves that screen and does not offer a way back.
