package com.smarthire.gui;

import com.smarthire.AppContext;
import com.smarthire.model.ApplicationStatus;
import com.smarthire.model.EmploymentType;
import com.smarthire.model.Job;
import com.smarthire.model.JobApplication;
import com.smarthire.model.JobStatus;
import com.smarthire.model.User;
import com.smarthire.model.UserRole;
import com.smarthire.service.ResumeService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Desktop interface for SmartHire. Run with com.smarthire.gui.SmartHireGui. */
public final class SmartHireGui extends JFrame {
    private static final Color NAVY = new Color(24, 39, 67);
    private static final Color BLUE = new Color(48, 105, 206);
    private static final Color PALE = new Color(244, 247, 252);
    private static final Color INK = new Color(34, 45, 63);

    private final AppContext app;
    private final JPanel root = new JPanel(new CardLayout());
    private User currentUser;
    private JPanel contentHost;
    private JLabel pageTitle;
    private List<Job> displayedJobs = new ArrayList<>();
    private List<JobApplication> displayedApplications = new ArrayList<>();
    private JTable jobsTable;
    private JTable applicationsTable;

    private SmartHireGui() {
        super("SmartHire | Recruitment workspace");
        app = new AppContext("smarthire_data");
        if (app.users().isEmpty()) {
            app.auth().register("System Admin", "admin@smarthire.com", "Admin@123", UserRole.ADMIN);
        }
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 680));
        setSize(1220, 790);
        setLocationRelativeTo(null);
        root.add(buildAuthPage(), "auth");
        setContentPane(root);
        ((CardLayout) root.getLayout()).show(root, "auth");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            SmartHireGui gui = new SmartHireGui();
            gui.setVisible(true);
        });
    }

    private JPanel buildAuthPage() {
        JPanel page = new JPanel(new GridLayout(1, 2));
        JPanel brand = new JPanel();
        brand.setBackground(NAVY);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setBorder(BorderFactory.createEmptyBorder(90, 54, 50, 44));
        JLabel brandName = new JLabel("SmartHire");
        brandName.setForeground(Color.WHITE);
        brandName.setFont(new Font("SansSerif", Font.BOLD, 42));
        JLabel tagline = new JLabel("A clearer way to hire.");
        tagline.setForeground(new Color(198, 215, 245));
        tagline.setFont(new Font("SansSerif", Font.PLAIN, 20));
        JLabel overview = new JLabel("Jobs, candidates, applications and hiring\nworkflows in one workspace.");
        overview.setForeground(new Color(230, 237, 249));
        overview.setFont(new Font("SansSerif", Font.PLAIN, 15));
        brand.add(brandName); brand.add(Box.createVerticalStrut(12));
        brand.add(tagline); brand.add(Box.createVerticalStrut(30)); brand.add(overview);

        JPanel formSide = new JPanel(new GridBagLikeLayout());
        formSide.setBackground(Color.WHITE);
        JPanel forms = new JPanel();
        forms.setOpaque(false);
        forms.setLayout(new BoxLayout(forms, BoxLayout.Y_AXIS));
        forms.setBorder(BorderFactory.createEmptyBorder(40, 42, 40, 42));
        JLabel heading = new JLabel("Welcome back");
        heading.setFont(new Font("SansSerif", Font.BOLD, 28)); heading.setForeground(INK);
        JLabel subheading = new JLabel("Sign in or create a candidate / recruiter account.");
        subheading.setForeground(new Color(105, 116, 135));
        JTextField email = field("Email address");
        JPasswordField password = new JPasswordField(); styleInput(password); password.setToolTipText("Password");
        JLabel emailLabel = fieldLabel("EMAIL");
        JLabel passwordLabel = fieldLabel("PASSWORD");
        JButton login = primaryButton("Sign in");
        JButton register = secondaryButton("Create an account");
        forms.add(heading); forms.add(Box.createVerticalStrut(8)); forms.add(subheading);
        forms.add(Box.createVerticalStrut(30)); forms.add(emailLabel); forms.add(Box.createVerticalStrut(7)); forms.add(email);
        forms.add(Box.createVerticalStrut(17)); forms.add(passwordLabel); forms.add(Box.createVerticalStrut(7)); forms.add(password);
        forms.add(Box.createVerticalStrut(22)); forms.add(login); forms.add(Box.createVerticalStrut(10)); forms.add(register);
        forms.add(Box.createVerticalStrut(25));
        JLabel hint = new JLabel("First run admin: admin@smarthire.com / Admin@123");
        hint.setForeground(new Color(120, 128, 142)); hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        forms.add(hint);
        login.addActionListener(e -> safely(() -> {
            User user = app.auth().login(email.getText().trim(), new String(password.getPassword()));
            currentUser = user;
            app.auditLogger().log(user.getEmail(), "LOGIN (GUI)");
            showWorkspace("Dashboard");
        }));
        register.addActionListener(e -> showRegisterDialog());
        formSide.add(forms);
        page.add(brand); page.add(formSide);
        return page;
    }

    /** Small GridBag-like center container without extra layout dependencies. */
    private static final class GridBagLikeLayout extends java.awt.GridBagLayout { }

    private void showRegisterDialog() {
        JTextField name = field("Full name");
        JTextField email = field("Email address");
        JPasswordField password = new JPasswordField(); styleInput(password);
        JComboBox<UserRole> role = new JComboBox<>(new UserRole[]{UserRole.CANDIDATE, UserRole.RECRUITER});
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 7));
        form.add(new JLabel("Full name")); form.add(name);
        form.add(new JLabel("Email")); form.add(email);
        form.add(new JLabel("Password (at least 6 characters)")); form.add(password);
        form.add(new JLabel("Account type")); form.add(role);
        int choice = JOptionPane.showConfirmDialog(this, form, "Create SmartHire account", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;
        safely(() -> {
            User user = app.auth().register(name.getText().trim(), email.getText().trim(),
                    new String(password.getPassword()), (UserRole) role.getSelectedItem());
            app.auditLogger().log(user.getEmail(), "REGISTER as " + user.getRole() + " (GUI)");
            JOptionPane.showMessageDialog(this, "Account created. You can now sign in.", "Account ready", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void showWorkspace(String pageName) {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(PALE);
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Color.WHITE);
        top.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        JLabel logo = new JLabel("SmartHire"); logo.setFont(new Font("SansSerif", Font.BOLD, 19)); logo.setForeground(NAVY);
        JLabel greeting = new JLabel(currentUser.getName() + "  ·  " + currentUser.getRole());
        greeting.setForeground(new Color(94, 104, 121));
        JButton logout = secondaryButton("Sign out");
        logout.addActionListener(e -> { currentUser = null; root.removeAll(); root.add(buildAuthPage(), "auth"); ((CardLayout) root.getLayout()).show(root, "auth"); root.revalidate(); root.repaint(); });
        top.add(logo, BorderLayout.WEST); top.add(greeting, BorderLayout.CENTER); top.add(logout, BorderLayout.EAST);
        workspace.add(top, BorderLayout.NORTH);

        JPanel sidebar = new JPanel(); sidebar.setBackground(NAVY); sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(BorderFactory.createEmptyBorder(20, 12, 20, 12));
        sidebar.setPreferredSize(new Dimension(205, 0));
        contentHost = new JPanel(new BorderLayout()); contentHost.setBackground(PALE);
        pageTitle = new JLabel(); pageTitle.setFont(new Font("SansSerif", Font.BOLD, 25)); pageTitle.setForeground(INK);
        JPanel titlePanel = new JPanel(new BorderLayout()); titlePanel.setOpaque(false); titlePanel.setBorder(BorderFactory.createEmptyBorder(25, 30, 15, 30)); titlePanel.add(pageTitle, BorderLayout.WEST);
        JPanel pageBody = new JPanel(new BorderLayout()); pageBody.setOpaque(false); pageBody.add(titlePanel, BorderLayout.NORTH);
        JPanel bodyHost = new JPanel(new BorderLayout()); bodyHost.setOpaque(false);
        pageBody.add(bodyHost, BorderLayout.CENTER);
        contentHost.add(pageBody, BorderLayout.CENTER);
        String[] nav;
        if (currentUser.getRole() == UserRole.CANDIDATE) nav = new String[]{"Dashboard", "Browse jobs", "My applications", "My profile"};
        else if (currentUser.getRole() == UserRole.RECRUITER) nav = new String[]{"Dashboard", "My jobs", "Applicants"};
        else nav = new String[]{"Dashboard", "All jobs", "All applications", "Users"};
        for (String item : nav) {
            JButton button = navButton(item);
            button.addActionListener(e -> showPage(item, bodyHost));
            sidebar.add(button); sidebar.add(Box.createVerticalStrut(8));
        }
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, contentHost);
        split.setDividerLocation(205); split.setDividerSize(0); split.setBorder(null); split.setResizeWeight(0);
        workspace.add(split, BorderLayout.CENTER);
        root.add(workspace, "workspace"); ((CardLayout) root.getLayout()).show(root, "workspace");
        showPage(pageName, bodyHost);
        root.revalidate(); root.repaint();
    }

    private void showPage(String name, JPanel bodyHost) {
        bodyHost.removeAll();
        pageTitle.setText(name);
        JPanel page;
        if ("Dashboard".equals(name)) page = dashboardPage();
        else if (name.toLowerCase().contains("job")) page = jobsPage();
        else if (name.toLowerCase().contains("applicant") || name.toLowerCase().contains("application")) page = applicationsPage();
        else if ("My profile".equals(name)) page = profilePage();
        else if ("Users".equals(name)) page = usersPage();
        else page = dashboardPage();
        bodyHost.add(page, BorderLayout.CENTER); bodyHost.revalidate(); bodyHost.repaint();
    }

    private JPanel dashboardPage() {
        JPanel panel = new JPanel(new BorderLayout(0, 18)); panel.setOpaque(false); panel.setBorder(BorderFactory.createEmptyBorder(5, 30, 30, 30));
        String report;
        if (currentUser.getRole() == UserRole.CANDIDATE) {
            List<JobApplication> mine = app.applicationService().listForCandidate(currentUser.getId());
            long open = app.jobService().listOpenJobs().size();
            report = "Open jobs available: " + open + "\nYour applications: " + mine.size() + "\n\nApplications by status:\n";
            for (ApplicationStatus status : ApplicationStatus.values()) {
                long count = mine.stream().filter(a -> a.getStatus() == status).count();
                report += String.format("  %-24s %d%n", status, count);
            }
        } else {
            int scope = currentUser.getRole() == UserRole.ADMIN ? -1 : currentUser.getId();
            report = app.dashboardService().buildReport(scope);
        }
        long jobCount = currentUser.getRole() == UserRole.CANDIDATE ? app.jobService().listOpenJobs().size()
                : currentUser.getRole() == UserRole.ADMIN ? app.jobService().listAllJobs().size() : app.jobService().listByRecruiter(currentUser.getId()).size();
        long appCount;
        if (currentUser.getRole() == UserRole.CANDIDATE) appCount = app.applicationService().listForCandidate(currentUser.getId()).size();
        else if (currentUser.getRole() == UserRole.ADMIN) appCount = app.applicationService().listAll().size();
        else appCount = applicationsForUser().size();
        JPanel cards = new JPanel(new GridLayout(1, 3, 16, 0)); cards.setOpaque(false);
        cards.add(metricCard(currentUser.getRole() == UserRole.CANDIDATE ? "Open opportunities" : "Jobs", String.valueOf(jobCount)));
        cards.add(metricCard("Applications", String.valueOf(appCount)));
        cards.add(metricCard("Signed in as", currentUser.getRole().toString()));
        JTextArea text = new JTextArea(report); text.setEditable(false); text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        text.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20)); text.setBackground(Color.WHITE); text.setForeground(INK);
        panel.add(cards, BorderLayout.NORTH); panel.add(new JScrollPane(text), BorderLayout.CENTER);
        return panel;
    }

    private JPanel metricCard(String label, String value) {
        JPanel card = new JPanel(); card.setBackground(Color.WHITE); card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(228, 233, 242)), BorderFactory.createEmptyBorder(18, 20, 18, 20)));
        JLabel l = new JLabel(label); l.setForeground(new Color(112, 121, 138)); l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JLabel v = new JLabel(value); v.setForeground(NAVY); v.setFont(new Font("SansSerif", Font.BOLD, 24));
        card.add(l); card.add(Box.createVerticalStrut(10)); card.add(v); return card;
    }

    private JPanel jobsPage() {
        JPanel panel = new JPanel(new BorderLayout(0, 14)); panel.setOpaque(false); panel.setBorder(BorderFactory.createEmptyBorder(0, 30, 28, 30));
        JPanel toolbar = new JPanel(new BorderLayout(10, 0)); toolbar.setOpaque(false);
        JTextField search = field("Search title, department or skills"); JButton find = primaryButton("Search");
        toolbar.add(search, BorderLayout.CENTER); toolbar.add(find, BorderLayout.EAST);
        String[] cols = {"ID", "Title", "Department", "Type", "Status", "Required skills", "Posted"};
        jobsTable = table(cols); JScrollPane scroll = new JScrollPane(jobsTable);
        JPanel actions = new JPanel(); actions.setOpaque(false); actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        JButton refresh = secondaryButton("Refresh"); actions.add(refresh);
        if (currentUser.getRole() == UserRole.CANDIDATE) {
            JButton apply = primaryButton("Apply to selected job"); actions.add(Box.createHorizontalStrut(10)); actions.add(apply);
            apply.addActionListener(e -> safely(() -> applyToSelectedJob()));
        } else {
            JButton create = primaryButton("Post a job"); actions.add(Box.createHorizontalStrut(10)); actions.add(create);
            JButton toggle = secondaryButton("Close / reopen selected"); actions.add(Box.createHorizontalStrut(10)); actions.add(toggle);
            JButton importCsv = secondaryButton("Import jobs CSV"); actions.add(Box.createHorizontalStrut(10)); actions.add(importCsv);
            JButton exportCsv = secondaryButton("Export jobs CSV"); actions.add(Box.createHorizontalStrut(10)); actions.add(exportCsv);
            create.addActionListener(e -> createJob());
            toggle.addActionListener(e -> safely(() -> toggleSelectedJob()));
            importCsv.addActionListener(e -> safely(() -> importJobsCsv(search.getText())));
            exportCsv.addActionListener(e -> safely(() -> exportJobsCsv()));
        }
        refresh.addActionListener(e -> refreshJobs(search.getText()));
        find.addActionListener(e -> refreshJobs(search.getText()));
        panel.add(toolbar, BorderLayout.NORTH); panel.add(scroll, BorderLayout.CENTER); panel.add(actions, BorderLayout.SOUTH);
        refreshJobs(""); return panel;
    }

    private void refreshJobs(String query) {
        if (jobsTable == null) return;
        List<Job> jobs;
        if (currentUser.getRole() == UserRole.CANDIDATE) jobs = app.jobService().searchOpenJobs(query);
        else if (currentUser.getRole() == UserRole.ADMIN) jobs = app.jobService().listAllJobs();
        else jobs = app.jobService().listByRecruiter(currentUser.getId());
        final String q = query == null ? "" : query.trim().toLowerCase();
        displayedJobs = jobs.stream().filter(j -> q.isEmpty() || (j.getTitle() + " " + j.getDepartment() + " " + String.join(" ", j.getRequiredSkills())).toLowerCase().contains(q)).collect(Collectors.toList());
        DefaultTableModel model = new DefaultTableModel(new Object[]{"ID", "Title", "Department", "Type", "Status", "Required skills", "Posted"}, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        for (Job job : displayedJobs) model.addRow(new Object[]{job.getId(), job.getTitle(), job.getDepartment(), job.getEmploymentType(), job.getStatus(), String.join(", ", job.getRequiredSkills()), job.getPostedDate()});
        jobsTable.setModel(model);
    }

    private void importJobsCsv(String searchQuery) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import job postings from CSV");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("CSV file (Excel: CSV UTF-8)", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        int imported = app.jobCsvService().importCsv(currentUser, chooser.getSelectedFile().toPath());
        app.auditLogger().log(currentUser.getEmail(), "IMPORTED " + imported + " job posting(s) from CSV (GUI)");
        refreshJobs(searchQuery);
        JOptionPane.showMessageDialog(this, imported + " job posting(s) imported as OPEN.", "Import complete", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportJobsCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export job postings to CSV");
        chooser.setSelectedFile(new File("smarthire-jobs.csv"));
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("CSV file (Excel-compatible)", "csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File selected = withExtension(chooser.getSelectedFile(), ".csv");
        if (selected.exists() && JOptionPane.showConfirmDialog(this, "Replace " + selected.getName() + "?", "Confirm overwrite", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        List<Job> jobs = currentUser.getRole() == UserRole.ADMIN
                ? app.jobService().listAllJobs()
                : app.jobService().listByRecruiter(currentUser.getId());
        app.jobCsvService().exportCsv(jobs, selected.toPath());
        app.auditLogger().log(currentUser.getEmail(), "EXPORTED " + jobs.size() + " job posting(s) to CSV (GUI)");
        JOptionPane.showMessageDialog(this, "CSV exported to:\n" + selected.getAbsolutePath(), "Export complete", JOptionPane.INFORMATION_MESSAGE);
    }

    private static File withExtension(File file, String extension) {
        if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(extension)) return file;
        return new File(file.getParentFile(), file.getName() + extension);
    }

    private void applyToSelectedJob() {
        int row = jobsTable.getSelectedRow();
        if (row < 0) throw new IllegalArgumentException("Select a job first.");
        Job job = displayedJobs.get(jobsTable.convertRowIndexToModel(row));
        JTextField skills = field(String.join(", ", currentUser.getSkills()));
        skills.setText(String.join(", ", currentUser.getSkills()));
        JTextArea letter = new JTextArea(5, 28); letter.setLineWrap(true); letter.setWrapStyleWord(true);
        JPanel form = new JPanel(new BorderLayout(0, 8));
        JPanel top = new JPanel(new GridLayout(0, 1, 0, 5)); top.add(new JLabel("Skills (comma separated)")); top.add(skills); top.add(new JLabel("Cover letter"));
        form.add(top, BorderLayout.NORTH); form.add(new JScrollPane(letter), BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this, form, "Apply: " + job.getTitle(), JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        List<String> skillList = splitSkills(skills.getText());
        JobApplication created = app.applicationService().apply(currentUser, job.getId(), skillList, letter.getText().trim());
        app.auditLogger().log(currentUser.getEmail(), "APPLY to job #" + job.getId() + " (GUI)");
        JOptionPane.showMessageDialog(this, String.format("Application submitted. Match score: %.1f%%", created.getScore()), "Application sent", JOptionPane.INFORMATION_MESSAGE);
    }

    private void createJob() {
        JTextField title = field("Job title"), department = field("Department"), skills = field("Java, communication");
        JTextArea description = new JTextArea(5, 30); description.setLineWrap(true); description.setWrapStyleWord(true);
        JComboBox<EmploymentType> type = new JComboBox<>(EmploymentType.values());
        JPanel form = new JPanel(); form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        addLabeledField(form, "Title", title); addLabeledField(form, "Department", department);
        addLabeledField(form, "Description", new JScrollPane(description));
        addLabeledField(form, "Required skills (comma separated)", skills);
        addLabeledField(form, "Employment type", type);
        int screenHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
        int viewportHeight = Math.max(240, Math.min(340, screenHeight - 400));
        JScrollPane formScroll = new JScrollPane(form, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setPreferredSize(new Dimension(460, viewportHeight));
        formScroll.getVerticalScrollBar().setUnitIncrement(18);
        if (JOptionPane.showConfirmDialog(this, formScroll, "Post a job", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        safely(() -> {
            Job job = app.jobService().postJob(currentUser, title.getText().trim(), description.getText().trim(), department.getText().trim(), splitSkills(skills.getText()), (EmploymentType) type.getSelectedItem());
            app.auditLogger().log(currentUser.getEmail(), "POSTED job #" + job.getId() + " (GUI)");
            refreshJobs(""); JOptionPane.showMessageDialog(this, "Job posted successfully.", "Job posted", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private static void addLabeledField(JPanel form, String label, JComponent field) {
        JLabel caption = new JLabel(label);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension preferred = field.getPreferredSize();
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        form.add(caption);
        form.add(Box.createVerticalStrut(4));
        form.add(field);
        form.add(Box.createVerticalStrut(10));
    }

    private void toggleSelectedJob() {
        int row = jobsTable.getSelectedRow();
        if (row < 0) throw new IllegalArgumentException("Select a job first.");
        Job job = displayedJobs.get(jobsTable.convertRowIndexToModel(row));
        boolean wasOpen = job.getStatus() == JobStatus.OPEN;
        if (wasOpen) app.jobService().closeJob(currentUser, job.getId());
        else app.jobService().reopenJob(currentUser, job.getId());
        app.auditLogger().log(currentUser.getEmail(), (wasOpen ? "CLOSED" : "REOPENED") + " job #" + job.getId() + " (GUI)");
        refreshJobs("");
    }

    private JPanel applicationsPage() {
        JPanel panel = new JPanel(new BorderLayout(0, 14)); panel.setOpaque(false); panel.setBorder(BorderFactory.createEmptyBorder(0, 30, 28, 30));
        applicationsTable = table(new String[]{"Application", "Job", "Candidate", "Match", "Status", "Applied"});
        panel.add(new JScrollPane(applicationsTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(); actions.setOpaque(false); actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        JButton refresh = secondaryButton("Refresh"); actions.add(refresh);
        if (currentUser.getRole() != UserRole.CANDIDATE) {
            JComboBox<ApplicationStatus> status = new JComboBox<>(ApplicationStatus.values());
            JButton update = primaryButton("Update selected status");
            JButton shortlist = secondaryButton("Shortlist top 5 for selected job");
            JButton saveResume = secondaryButton("Save selected resume");
            actions.add(Box.createHorizontalStrut(10)); actions.add(status); actions.add(Box.createHorizontalStrut(8)); actions.add(update); actions.add(Box.createHorizontalStrut(8)); actions.add(shortlist); actions.add(Box.createHorizontalStrut(8)); actions.add(saveResume);
            update.addActionListener(e -> safely(() -> {
                int row = applicationsTable.getSelectedRow(); if (row < 0) throw new IllegalArgumentException("Select an application first.");
                JobApplication item = displayedApplications.get(applicationsTable.convertRowIndexToModel(row));
                app.applicationService().updateStatus(currentUser, item.getId(), (ApplicationStatus) status.getSelectedItem());
                app.auditLogger().log(currentUser.getEmail(), "UPDATED application #" + item.getId() + " status (GUI)");
                refreshApplications();
            }));
            shortlist.addActionListener(e -> safely(() -> {
                int row = applicationsTable.getSelectedRow(); if (row < 0) throw new IllegalArgumentException("Select an application first.");
                JobApplication item = displayedApplications.get(applicationsTable.convertRowIndexToModel(row));
                List<JobApplication> shortlisted = app.applicationService().shortlistTop(currentUser, item.getJobId(), 5);
                app.auditLogger().log(currentUser.getEmail(), "SHORTLISTED top 5 for job #" + item.getJobId() + " (GUI)");
                refreshApplications(); JOptionPane.showMessageDialog(this, shortlisted.size() + " candidate(s) shortlisted.");
            }));
            saveResume.addActionListener(e -> safely(() -> saveSelectedApplicantResume()));
        }
        refresh.addActionListener(e -> refreshApplications());
        panel.add(actions, BorderLayout.SOUTH); refreshApplications(); return panel;
    }

    private List<JobApplication> applicationsForUser() {
        List<JobApplication> apps = app.applicationService().listAll();
        if (currentUser.getRole() == UserRole.CANDIDATE) return app.applicationService().listForCandidate(currentUser.getId());
        if (currentUser.getRole() == UserRole.ADMIN) return apps;
        Set<Integer> ownedJobs = app.jobService().listByRecruiter(currentUser.getId()).stream().map(Job::getId).collect(Collectors.toSet());
        return apps.stream().filter(a -> ownedJobs.contains(a.getJobId())).collect(Collectors.toList());
    }

    private void refreshApplications() {
        if (applicationsTable == null) return;
        displayedApplications = applicationsForUser();
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Application", "Job", "Candidate", "Match", "Status", "Applied"}, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        for (JobApplication item : displayedApplications) {
            Job job = app.jobService().getById(item.getJobId());
            User candidate = app.users().findById(item.getCandidateId()).orElse(null);
            model.addRow(new Object[]{"#" + item.getId(), job.getTitle(), candidate == null ? "Unknown" : candidate.getName(), String.format("%.1f%%", item.getScore()), item.getStatus(), item.getAppliedDate()});
        }
        applicationsTable.setModel(model);
    }

    private JPanel profilePage() {
        JPanel panel = new JPanel(new BorderLayout(0, 14)); panel.setOpaque(false); panel.setBorder(BorderFactory.createEmptyBorder(0, 30, 30, 30));
        JPanel card = new JPanel(new GridLayout(0, 1, 0, 9)); card.setBackground(Color.WHITE); card.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JLabel name = new JLabel("Name: " + currentUser.getName()); JLabel email = new JLabel("Email: " + currentUser.getEmail());
        JTextField skills = field("Java, SQL, communication"); skills.setText(String.join(", ", currentUser.getSkills()));
        JButton save = primaryButton("Save skill profile");
        card.add(name); card.add(email); card.add(new JLabel("Skills (comma separated)")); card.add(skills); card.add(save);
        save.addActionListener(e -> safely(() -> {
            currentUser = app.auth().updateSkills(currentUser, splitSkills(skills.getText()));
            app.auditLogger().log(currentUser.getEmail(), "UPDATED candidate skill profile (GUI)");
            JOptionPane.showMessageDialog(this, "Skill profile saved.", "Profile updated", JOptionPane.INFORMATION_MESSAGE);
        }));
        if (currentUser.getRole() == UserRole.CANDIDATE) {
            JLabel resumeStatus = new JLabel(app.resumeService().fileName(currentUser.getId())
                    .map(fileName -> "Current resume: " + fileName)
                    .orElse("No resume uploaded yet."));
            JButton upload = secondaryButton("Upload / replace resume (PDF/DOCX, max 15 MB)");
            card.add(new JLabel("Resume attachment")); card.add(resumeStatus); card.add(upload);
            upload.addActionListener(e -> safely(() -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Choose your resume");
                chooser.setAcceptAllFileFilterUsed(false);
                chooser.setFileFilter(new FileNameExtensionFilter("PDF or Word document (*.pdf, *.docx)", "pdf", "docx"));
                if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                String savedName = app.resumeService().upload(currentUser, chooser.getSelectedFile().toPath());
                resumeStatus.setText("Current resume: " + savedName);
                app.auditLogger().log(currentUser.getEmail(), "UPLOADED/REPLACED candidate resume (GUI)");
                JOptionPane.showMessageDialog(this, "Resume uploaded and encrypted in local storage.", "Resume saved", JOptionPane.INFORMATION_MESSAGE);
            }));
        }
        panel.add(card, BorderLayout.NORTH); return panel;
    }

    private void saveSelectedApplicantResume() {
        int row = applicationsTable.getSelectedRow();
        if (row < 0) throw new IllegalArgumentException("Select an applicant first.");
        JobApplication application = displayedApplications.get(applicationsTable.convertRowIndexToModel(row));
        ResumeService.ResumeDocument resume = app.resumeService().loadForApplication(currentUser, application.getId());
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save applicant resume");
        chooser.setSelectedFile(new File(resume.getFileName()));
        String name = resume.getFileName().toLowerCase(java.util.Locale.ROOT);
        chooser.setFileFilter(name.endsWith(".pdf")
                ? new FileNameExtensionFilter("PDF document (*.pdf)", "pdf")
                : new FileNameExtensionFilter("Word document (*.docx)", "docx"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File target = withExtension(chooser.getSelectedFile(), name.endsWith(".pdf") ? ".pdf" : ".docx");
        if (target.exists() && JOptionPane.showConfirmDialog(this, "Replace " + target.getName() + "?", "Confirm overwrite", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            Files.write(target.toPath(), resume.getContent());
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not save the resume: " + e.getMessage());
        }
        app.auditLogger().log(currentUser.getEmail(), "SAVED applicant resume for application #" + application.getId() + " (GUI)");
        JOptionPane.showMessageDialog(this, "Resume saved to:\n" + target.getAbsolutePath(), "Resume downloaded", JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel usersPage() {
        JPanel panel = new JPanel(new BorderLayout()); panel.setOpaque(false); panel.setBorder(BorderFactory.createEmptyBorder(0, 30, 30, 30));
        JTable table = table(new String[]{"ID", "Name", "Email", "Role"});
        DefaultTableModel model = new DefaultTableModel(new Object[]{"ID", "Name", "Email", "Role"}, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };
        app.users().findAll().forEach(user -> model.addRow(new Object[]{user.getId(), user.getName(), user.getEmail(), user.getRole()}));
        table.setModel(model); panel.add(new JScrollPane(table), BorderLayout.CENTER); return panel;
    }

    private void safely(Runnable action) {
        try { action.run(); }
        catch (RuntimeException ex) {
            String message = ex.getMessage() == null ? "The action could not be completed." : ex.getMessage();
            JOptionPane.showMessageDialog(this, message, "SmartHire", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static List<String> splitSkills(String text) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().collect(Collectors.toList());
    }

    private static JTable table(String[] columns) {
        JTable table = new JTable(new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        });
        table.setRowHeight(30); table.setFillsViewportHeight(true); table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13)); table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(235, 240, 248)); table.setGridColor(new Color(232, 236, 243));
        return table;
    }

    private static JTextField field(String placeholder) {
        JTextField field = new JTextField(); styleInput(field); field.setToolTipText(placeholder); return field;
    }

    private static void styleInput(JTextField field) {
        field.setFont(new Font("SansSerif", Font.PLAIN, 14)); field.setPreferredSize(new Dimension(280, 40));
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(211, 219, 231)), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text); label.setForeground(new Color(104, 114, 132)); label.setFont(new Font("SansSerif", Font.BOLD, 11)); return label;
    }

    private static JButton primaryButton(String text) {
        JButton button = baseButton(text, BLUE, Color.WHITE);
        button.setBorderPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 13)); button.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15)); return button;
    }

    private static JButton secondaryButton(String text) {
        JButton button = baseButton(text, Color.WHITE, NAVY);
        button.setFont(new Font("SansSerif", Font.BOLD, 12)); button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(211, 219, 231)), BorderFactory.createEmptyBorder(8, 13, 8, 13))); return button;
    }

    private static JButton navButton(String text) {
        JButton button = baseButton(text, NAVY, new Color(230, 237, 249));
        button.setHorizontalAlignment(SwingConstants.LEFT); button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        button.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 10));
        button.setFont(new Font("SansSerif", Font.PLAIN, 14)); return button;
    }

    /** Uses a predictable renderer so Windows native themes cannot make button labels blend into their fill. */
    private static JButton baseButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setUI(new BasicButtonUI());
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setRolloverEnabled(false);
        return button;
    }
}
