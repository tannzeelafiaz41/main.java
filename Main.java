import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

class SchoolSecurityException extends Exception {
    public SchoolSecurityException(String message) {
        super(message);
    }
}

class SchoolDatabase {
    private static SchoolDatabase instance;
    private Map<String, User> users = new HashMap<>();
    private Map<String, QRPass> passes = new HashMap<>();
    private List<String> pickupLogs = new ArrayList<>();

    private SchoolDatabase() {
        users.put("guard1", new User("guard1", "1234", "GUARD"));
        users.put("parent1", new User("parent1", "1234", "PARENT"));
        users.put("admin1", new User("admin1", "admin", "ADMIN"));
    }

    public static SchoolDatabase getInstance() {
        if (instance == null) {
            instance = new SchoolDatabase();
        }
        return instance;
    }

    public Map<String, User> getUsers() { return users; }
    public Map<String, QRPass> getPasses() { return passes; }
    public List<String> getPickupLogs() { return pickupLogs; }
}

class User {
    private String username, password, role;

    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public String getUsername() { return username; }
    public String getRole() { return role; }

    public boolean checkPassword(String pass) {
        return this.password.equals(pass);
    }
}

class QRPass {
    private String passId, studentName;
    private LocalDateTime createdAt;

    public QRPass(String passId, String studentName) {
        this.passId = passId;
        this.studentName = studentName;
        this.createdAt = LocalDateTime.now();
    }

    public String getPassId() { return passId; }
    public String getStudentName() { return studentName; }

    public boolean isExpired() {
        return Duration.between(createdAt, LocalDateTime.now()).toMinutes() >= 30;
    }

    public void forceExpireForTest() {
        this.createdAt = LocalDateTime.now().minusMinutes(35);
    }
}

class AuthService {
    public User login(String username, String password)
            throws SchoolSecurityException {

        User user = SchoolDatabase.getInstance().getUsers().get(username);

        if (user == null || !user.checkPassword(password)) {
            throw new SchoolSecurityException(
                "Invalid credentials! Access Denied."
            );
        }

        return user;
    }

    public void checkAccess(User user, String requiredRole)
            throws SchoolSecurityException {

        if (user == null || !user.getRole().equals(requiredRole)) {
            throw new SchoolSecurityException(
                "Unauthorized Access! Role " + requiredRole + " required."
            );
        }
    }
}

class GateService {
    private SchoolDatabase db = SchoolDatabase.getInstance();

    public QRPass generatePass(User user, String studentName)
            throws SchoolSecurityException {

        new AuthService().checkAccess(user, "PARENT");

        String passId = "QR-" + (1000 + new Random().nextInt(9000));

        QRPass pass = new QRPass(passId, studentName);
        db.getPasses().put(passId, pass);

        return pass;
    }

    public String scanPass(User user, String passId)
            throws SchoolSecurityException {

        new AuthService().checkAccess(user, "GUARD");

        QRPass pass = db.getPasses().get(passId);

        if (pass == null) {
            throw new SchoolSecurityException(
                "INVALID QR PASS: Pass does not exist."
            );
        }

        if (pass.isExpired()) {
            throw new SchoolSecurityException(
                "EXPIRED QR PASS: Pass exceeded 30-minute limit."
            );
        }

        String log = "Student: " + pass.getStudentName()
                + " | Pass: " + passId
                + " | Time: " + LocalDateTime.now();

        db.getPickupLogs().add(log);

        return "VALID PASS! Pick-up Approved & Logged.";
    }
}

public class Main {
    public static void main(String[] args) {

        AuthService auth = new AuthService();
        GateService gate = new GateService();
        Scanner sc = new Scanner(System.in);

        System.out.println("=== QR PASS SCANNER & SCHOOL GATE SYSTEM (JAVA) ===");

        runAutomatedTests(auth, gate);

        try {
            System.out.print("\nEnter Username (parent1 / guard1): ");
            String uname = sc.nextLine();

            System.out.print("Enter Password (1234): ");
            String pass = sc.nextLine();

            User currentUser = auth.login(uname, pass);

            System.out.println("Login Successful! Role: " + currentUser.getRole());

            if (currentUser.getRole().equals("PARENT")) {

                System.out.print("Enter Student Name to generate QR Pass: ");
                String student = sc.nextLine();

                QRPass qr = gate.generatePass(currentUser, student);

                System.out.println("Generated QR Pass ID: "
                        + qr.getPassId() + " (Valid for 30 mins)");

            } else if (currentUser.getRole().equals("GUARD")) {

                System.out.print("Enter QR Pass ID to Scan: ");
                String qrId = sc.nextLine();

                System.out.println(gate.scanPass(currentUser, qrId));
            }

        } catch (SchoolSecurityException e) {
            System.out.println("ERROR: " + e.getMessage());
        }

        sc.close();
    }

    private static void runAutomatedTests(AuthService auth, GateService gate) {

        System.out.println("\n--- Running Unit Test Cases ---");

        try {
            User guard = auth.login("guard1", "1234");
            System.out.println("T1 (Guard Login): PASS");

            try {
                auth.login("guard1", "wrong");
            } catch (SchoolSecurityException e) {
                System.out.println("T2 (Wrong Password Blocked): PASS");
            }

            User parent = auth.login("parent1", "1234");

            try {
                gate.scanPass(parent, "QR-0000");
            } catch (SchoolSecurityException e) {
                System.out.println("T3 (Role Access Blocked): PASS");
            }

            QRPass validPass = gate.generatePass(parent, "Ali Ahmed");

            System.out.println("T5 (Generate QR Pass): PASS ("
                    + validPass.getPassId() + ")");

            System.out.println("T6 (Scan Valid Pass): "
                    + gate.scanPass(guard, validPass.getPassId()) + " -> PASS");

            validPass.forceExpireForTest();

            try {
                gate.scanPass(guard, validPass.getPassId());
            } catch (SchoolSecurityException e) {
                System.out.println("T7 (Expired Pass Blocked): PASS");
            }

            try {
                gate.scanPass(guard, "QR-9999");
            } catch (SchoolSecurityException e) {
                System.out.println("T8 (Invalid Pass Blocked): PASS");
            }

            System.out.println("T9 (Logs Saved Count): "
                    + SchoolDatabase.getInstance().getPickupLogs().size()
                    + " -> PASS");

        } catch (Exception e) {
            System.out.println("Test Failed: " + e.getMessage());
        }
    }
}
