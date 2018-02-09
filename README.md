# FlightsDB

A JDBC application that simulates the process of booking flight tickets in real life

Screenshot:
![screenshot](https://raw.githubusercontent.com/EmilyCheoh/FlightsDB/master/screenshot.png)

* _**Login**_ takes a user's handle (a short username) and password and checks that they exist in the database.

* _**Search**_ shows a list of all the one- and two-hop itineraries for flying from the given origin to the given destination on the given day of the month. (The search is always limited to the month of July 2015, so the user only needs to specify which day in that month.)

* _**Book**_ allows the logged in user to reserve seats on the flights of an itinerary just printed by search. They do so by passing in the number listed next to that itinerary in the search output.

* _**Reservations**_ shows all of the logged in user's current reservations.

* _**Cancel**_ allows the logged in user to cancel a reservation made earlier. As with book, they do so by passing in the number listed next to that itinerary in the reservations output.

