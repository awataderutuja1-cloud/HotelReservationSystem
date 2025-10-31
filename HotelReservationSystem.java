import java.util.*;

class Room {
    int roomNumber;
    String category; // Standard, Deluxe, Suite
    boolean isBooked;
    double price;

    Room(int roomNumber, String category, double price) {
        this.roomNumber = roomNumber;
        this.category = category;
        this.price = price;
        this.isBooked = false;
    }
}

class Reservation {
    String customerName;
    Room room;
    int days;
    double totalAmount;

    Reservation(String customerName, Room room, int days) {
        this.customerName = customerName;
        this.room = room;
        this.days = days;
        this.totalAmount = room.price * days;
    }

    void showDetails() {
        System.out.println("👤 Customer: " + customerName);
        System.out.println("🏨 Room No: " + room.roomNumber + " (" + room.category + ")");
        System.out.println("📅 Days: " + days);
        System.out.println("💰 Amount: ₹" + totalAmount);
    }
}

class Hotel {
    List<Room> rooms = new ArrayList<>();
    List<Reservation> reservations = new ArrayList<>();

    Hotel() {
        // Adding sample rooms
        rooms.add(new Room(101, "Standard", 2000));
        rooms.add(new Room(102, "Deluxe", 3500));
        rooms.add(new Room(103, "Suite", 6000));
        rooms.add(new Room(104, "Standard", 2000));
        rooms.add(new Room(105, "Deluxe", 3500));
    }

    void showAvailableRooms() {
        System.out.println("\n🏨 Available Rooms:");
        for (Room r : rooms) {
            if (!r.isBooked) {
                System.out.println("Room No: " + r.roomNumber + " | " + r.category + " | ₹" + r.price);
            }
        }
    }

    void bookRoom(String name, int roomNo, int days) {
        for (Room r : rooms) {
            if (r.roomNumber == roomNo && !r.isBooked) {
                r.isBooked = true;
                Reservation res = new Reservation(name, r, days);
                reservations.add(res);
                System.out.println("✅ Booking Successful!");
                res.showDetails();
                return;
            }
        }
        System.out.println("❌ Room not available or already booked!");
    }

    void cancelBooking(String name, int roomNo) {
        Iterator<Reservation> itr = reservations.iterator();
        while (itr.hasNext()) {
            Reservation res = itr.next();
            if (res.customerName.equalsIgnoreCase(name) && res.room.roomNumber == roomNo) {
                res.room.isBooked = false;
                itr.remove();
                System.out.println("❌ Booking cancelled for " + name + " (Room " + roomNo + ")");
                return;
            }
        }
        System.out.println("❌ No booking found for cancellation!");
    }

    void showAllBookings() {
        if (reservations.isEmpty()) {
            System.out.println("No bookings yet.");
            return;
        }
        System.out.println("\n📋 Current Reservations:");
        for (Reservation r : reservations) {
            r.showDetails();
            System.out.println("---------------------");
        }
    }
}

public class HotelReservationSystem {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Hotel hotel = new Hotel();

        while (true) {
            System.out.println("\n--- Hotel Reservation Menu ---");
            System.out.println("1. Show Available Rooms");
            System.out.println("2. Book a Room");
            System.out.println("3. Cancel Booking");
            System.out.println("4. Show All Bookings");
            System.out.println("5. Exit");
            System.out.print("Enter choice: ");
            int choice = sc.nextInt();

            switch (choice) {
                case 1:
                    hotel.showAvailableRooms();
                    break;

                case 2:
                    System.out.print("Enter your name: ");
                    sc.nextLine();
                    String name = sc.nextLine();
                    System.out.print("Enter room number: ");
                    int roomNo = sc.nextInt();
                    System.out.print("Enter number of days: ");
                    int days = sc.nextInt();
                    hotel.bookRoom(name, roomNo, days);
                    break;

                case 3:
                    System.out.print("Enter your name: ");
                    sc.nextLine();
                    String cname = sc.nextLine();
                    System.out.print("Enter room number: ");
                    int rNo = sc.nextInt();
                    hotel.cancelBooking(cname, rNo);
                    break;

                case 4:
                    hotel.showAllBookings();
                    break;

                case 5:
                    System.out.println("👋 Exiting... Thank you for using Hotel Reservation System!");
                    sc.close();
                    return;

                default:
                    System.out.println("❌ Invalid choice!");
            }
        }
    }
}
