import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.*;

/**
 * Allows clients to query and update the database in order to log in, search
 * for flights, reserve seats, show reservations, and cancel reservations.
 */
public class FlightsDB {

    /** Maximum number of reservations to allow on one flight. */
    private static int MAX_FLIGHT_BOOKINGS = 3;

    /** Holds the connection to the database. */
    private Connection conn;

    /** Opens a connection to the database using the given settings. */
    public void open(Properties settings) throws Exception {
        // Make sure the JDBC driver is loaded.
        String driverClassName = settings.getProperty("flightservice.jdbc_driver");
        Class.forName(driverClassName).newInstance();

        // Open a connection to our database.
        conn = DriverManager.getConnection(settings.getProperty("flightservice.url"),
                settings.getProperty("flightservice.sqlazure_username"),
                settings.getProperty("flightservice.sqlazure_password"));

        conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
    }

    /** Closes the connection to the database. */
    public void close() throws SQLException {
        conn.close();
        conn = null;
    }

    // SQL statements with spaces left for parameters:
    private PreparedStatement beginTxnStmt;
    private PreparedStatement commitTxnStmt;
    private PreparedStatement abortTxnStmt;
    private PreparedStatement pstmt;
    private PreparedStatement pstmt2;
    private PreparedStatement userAuth;
    private PreparedStatement reservationQuery;
    private PreparedStatement remove;
    private PreparedStatement bookOnSameDay;
    private PreparedStatement moreThanThree;
    private PreparedStatement add;

    /** Performs additional preparation after the connection is opened. */
    public void prepare() throws SQLException {
        // NOTE: We must explicitly set the isolation level to SERIALIZABLE as it
        //       defaults to allowing non-repeatable reads.
        beginTxnStmt = conn.prepareStatement("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE; BEGIN TRANSACTION;");
        commitTxnStmt = conn.prepareStatement("COMMIT TRANSACTION");
        abortTxnStmt = conn.prepareStatement("ROLLBACK TRANSACTION");

        // TODO: create more prepared statements here
        pstmt = conn.prepareStatement(
                "SELECT TOP (99) fid, name, flight_num, origin_city, dest_city, actual_time FROM Flights F1, Carriers WHERE carrier_id = cid AND actual_time IS NOT NULL AND year = ? AND month_id = ? AND day_of_month = ? AND origin_city = ? AND dest_city = ? ORDER BY actual_time ASC");
        pstmt2 = conn.prepareStatement(
                "SELECT TOP (99) F1.fid as fid1, C1.name as name1, F1.flight_num as flight_num1, F1.origin_city as origin_city1, F1.dest_city as dest_city1, F1.actual_time as actual_time1, F2.fid as fid2, C2.name as name2, F2.flight_num as flight_num2, F2.origin_city as origin_city2, F2.dest_city as dest_city2, F2.actual_time as actual_time2 FROM Flights F1, Flights F2, Carriers C1, Carriers C2 WHERE F1.carrier_id = C1.cid AND F1.actual_time IS NOT NULL AND F2.carrier_id = C2.cid AND F2.actual_time IS NOT NULL AND F1.year = ? AND F1.month_id = ? AND F1.day_of_month = ? AND F2.year = ? AND F2.month_id = ? AND F2.day_of_month = ? AND F1.origin_city = ? AND F2.dest_city = ? AND F1.dest_city = F2.origin_city ORDER BY F1.actual_time + F2.actual_time ASC");
        userAuth = conn.prepareStatement("SELECT customerID, fullname FROM Customer WHERE handle = ? AND password = ?");
        reservationQuery = conn.prepareStatement(
                "SELECT * FROM Reservation R JOIN Customer C ON R.custID = C.customerID JOIN Flights F ON R.flightID = F.fid JOIN Carriers CA ON F.carrier_id = CA.cid WHERE customerID = ?");
        remove = conn.prepareStatement("DELETE FROM Reservation WHERE custID = ? AND flightID = ?");
        add = conn.prepareStatement("INSERT INTO Reservation VALUES (?, ?)");
        bookOnSameDay = conn.prepareStatement(
                "SELECT * FROM Reservation R JOIN Flights F ON R.flightID = F.fid JOIN Customer C ON R.custID = C.customerID WHERE f.year = ? AND f.month_id = ? AND f.day_of_month = ? AND R.custID = ?");
        moreThanThree = conn.prepareStatement(
                "SELECT R.flightID, COUNT(flightID) FROM Reservation R JOIN Flights F ON R.flightID = F.fid WHERE R.flightID = ? GROUP BY R.flightID HAVING COUNT(flightID) > 2");
    }

    /**
     * Tries to log in as the given user.
     * @returns The authenticated user or null if login failed.
     */
    public User logIn(String handle, String password) throws SQLException {

        User results;

        userAuth.clearParameters();
        userAuth.setString(1, handle);
        userAuth.setString(2, password);

        ResultSet loginResults = userAuth.executeQuery();

        if (loginResults.next()) {
            results = new User(loginResults.getInt("customerID"), handle, loginResults.getString("fullname"));
        } else {
            results = null;
        }
        loginResults.close();

        return results;
    }

    /**
     * Returns the list of all flights between the given cities on the given day.
     */
    public List<Flight[]> getFlights(int year, int month, int dayOfMonth, String originCity, String destCity)
            throws SQLException {

        List<Flight[]> results = new ArrayList<Flight[]>();

        pstmt.clearParameters();
        pstmt.setInt(1, year);
        pstmt.setInt(2, month);
        pstmt.setInt(3, dayOfMonth);
        pstmt.setString(4, originCity);
        pstmt.setString(5, destCity);

        ResultSet directResults = pstmt.executeQuery();

        while (directResults.next()) {
            results.add(new Flight[] {
                    new Flight(directResults.getInt("fid"), year, month, dayOfMonth, directResults.getString("name"),
                            directResults.getString("flight_num"), directResults.getString("origin_city"),
                            directResults.getString("dest_city"), (int) directResults.getFloat("actual_time")) });
        }
        directResults.close();

        pstmt2.clearParameters();
        pstmt2.setInt(1, year);
        pstmt2.setInt(2, month);
        pstmt2.setInt(3, dayOfMonth);
        pstmt2.setInt(4, year);
        pstmt2.setInt(5, month);
        pstmt2.setInt(6, dayOfMonth);
        pstmt2.setString(7, originCity);
        pstmt2.setString(8, destCity);

        ResultSet twoHopResults = pstmt2.executeQuery();
        while (twoHopResults.next()) {
            results.add(new Flight[] {
                    new Flight(twoHopResults.getInt("fid1"), year, month, dayOfMonth, twoHopResults.getString("name1"),
                            twoHopResults.getString("flight_num1"), twoHopResults.getString("origin_city1"),
                            twoHopResults.getString("dest_city1"), (int) twoHopResults.getFloat("actual_time1")),
                    new Flight(twoHopResults.getInt("fid2"), year, month, dayOfMonth, twoHopResults.getString("name2"),
                            twoHopResults.getString("flight_num2"), twoHopResults.getString("origin_city2"),
                            twoHopResults.getString("dest_city2"), (int) twoHopResults.getFloat("actual_time2")) });
        }

        twoHopResults.close();

        return results;
    }

    /** Returns the list of all flights reserved by the given user. */
    public List<Flight> getReservations(int userid) throws SQLException {

        List<Flight> results = new ArrayList<Flight>();

        reservationQuery.clearParameters();
        reservationQuery.setInt(1, userid);

        ResultSet reservationResults = reservationQuery.executeQuery();

        while (reservationResults.next()) {
            results.add(new Flight(reservationResults.getInt("fid"), reservationResults.getInt("year"),
                    reservationResults.getInt("month_id"), reservationResults.getInt("day_of_month"),
                    reservationResults.getString("name"), reservationResults.getString("flight_num"),
                    reservationResults.getString("origin_city"), reservationResults.getString("dest_city"),
                    reservationResults.getInt("actual_time")));
        }
        reservationResults.close();

        return results;
    }

    /** Indicates that a reservation was added successfully. */
    public static final int RESERVATION_ADDED = 1;

    /**
     * Indicates the reservation could not be made because the flight is full
     * (i.e., 3 users have already booked).
     */
    public static final int RESERVATION_FLIGHT_FULL = 2;

    /**
     * Indicates the reservation could not be made because the user already has a
     * reservation on that day.
     */
    public static final int RESERVATION_DAY_FULL = 3;

    /**
     * Attempts to add a reservation for the given user on the given flights, all
     * occurring on the given day.
     * @returns One of the {@code RESERVATION_*} codes above.
     */
    public int addReservations(int userid, int year, int month, int dayOfMonth, List<Flight> flights)
            throws SQLException, IOException {

        // TODO: implement this in a transaction (see beginTransaction etc. below)
        beginTransaction();

        ResultSet sameDayResultSet;
        ResultSet moreThanThrResultSet;

        for (Flight f : flights) {
            bookOnSameDay.clearParameters();
            bookOnSameDay.setInt(1, f.year);
            bookOnSameDay.setInt(2, f.month);
            bookOnSameDay.setInt(3, f.dayOfMonth);
            bookOnSameDay.setInt(4, userid);
            sameDayResultSet = bookOnSameDay.executeQuery();

            if (sameDayResultSet.next()) {
                sameDayResultSet.close();
                rollbackTransaction();
                return RESERVATION_DAY_FULL;
            }
        }

        for (Flight f : flights) {
            moreThanThree.clearParameters();
            moreThanThree.setInt(1, f.id);
            moreThanThrResultSet = moreThanThree.executeQuery();

            if (moreThanThrResultSet.next()) {
                moreThanThrResultSet.close();
                rollbackTransaction();
                return RESERVATION_FLIGHT_FULL;
            }
        }

        /* System.out.println("Press any key to continue...");
        char a = (char) System.in.read(); */

        for (Flight f : flights) {
            add.clearParameters();
            add.setInt(1, userid);
            add.setInt(2, f.id);
            add.executeUpdate();
        }

        commitTransaction();

        return RESERVATION_ADDED;
    }

    /** Cancels all reservations for the given user on the given flights. */
    public void removeReservations(int userid, List<Flight> flights) throws SQLException {

        beginTransaction();

        for (Flight f : flights) {
            remove.clearParameters();
            remove.setInt(1, userid);
            remove.setInt(2, f.id);
            remove.executeUpdate();
        }

        commitTransaction();
    }

    /** Puts the connection into a new transaction. */
    public void beginTransaction() throws SQLException {
        conn.setAutoCommit(false); // do not commit until explicitly requested
        beginTxnStmt.executeUpdate();
    }

    /** Commits the current transaction. */
    public void commitTransaction() throws SQLException {
        commitTxnStmt.executeUpdate();
        conn.setAutoCommit(true); // go back to one transaction per statement
    }

    /** Aborts the current transaction. */
    public void rollbackTransaction() throws SQLException {
        abortTxnStmt.executeUpdate();
        conn.setAutoCommit(true); // go back to one transaction per statement
    }
}
