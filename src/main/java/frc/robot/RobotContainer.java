// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.epilogue.Logged;
import edu.wpi.first.epilogue.NotLogged;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.trajectory.Trajectory;
import edu.wpi.first.math.trajectory.TrajectoryConfig;
import edu.wpi.first.math.trajectory.TrajectoryGenerator;
import edu.wpi.first.wpilibj.PS5Controller;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.XboxController.Button;
import frc.robot.Constants.AutoConstants;
import frc.robot.Constants.DriveConstants;
import frc.robot.Constants.OIConstants;
import frc.robot.subsystems.DriveSubsystem;
import frc.robot.subsystems.Spinners;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.MecanumControllerCommand;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.button.JoystickButton;
import java.util.List;

/*
 * This class is where the bulk of the robot should be declared.  Since Command-based is a
 * "declarative" paradigm, very little robot logic should actually be handled in the {@link Robot}
 * periodic methods (other than the scheduler calls).  Instead, the structure of the robot
 * (including subsystems, commands, and button mappings) should be declared here.
 */
@Logged
public class RobotContainer {
  // The robot's subsystems
  private final DriveSubsystem m_robotDrive = new DriveSubsystem();

  private final Spinners m_spinners = new Spinners();

  @NotLogged
  // The driver's controller
  private final PS5Controller m_driverController = new PS5Controller(OIConstants.kDriverControllerPort);

  /** The container for the robot. Contains subsystems, OI devices, and commands. */
  public RobotContainer() {
    // Configure the button bindings
    configureButtonBindings();

    // Configure default commands
    // Set the default drive command to split-stick arcade drive
    m_robotDrive.setDefaultCommand(
        // A split-stick arcade command, with forward/backward controlled by the left
        // hand, and turning controlled by the right.
        new RunCommand(
            () ->
                m_robotDrive.drive(
                    -m_driverController.getLeftY(),
                    m_driverController.getRawButton(3) ? -0.5 : m_driverController.getRawButton(5) ? 0.5 : 0,
                    m_driverController.getLeftX(),
                    false),
            m_robotDrive));

    m_spinners.setDefaultCommand(
        new RunCommand(() -> {
            double left = m_driverController.getRightX();
            double right = m_driverController.getRawButton(10) ? 1 : 0;
            m_spinners.setLeft(left);
            m_spinners.setRight(right);
        }, m_spinners)
    );
  }

  /**
   * Use this method to define your button->command mappings. Buttons can be created by
   * instantiating a {@link edu.wpi.first.wpilibj.GenericHID} or one of its subclasses ({@link
   * edu.wpi.first.wpilibj.Joystick} or {@link XboxController}), and then calling passing it to a
   * {@link JoystickButton}.
   */
  private void configureButtonBindings() {
    // Drive at half speed when the right bumper is held
    new JoystickButton(m_driverController, Button.kRightBumper.value)
        .onTrue(new InstantCommand(() -> m_robotDrive.setMaxOutput(0.5)))
        .onFalse(new InstantCommand(() -> m_robotDrive.setMaxOutput(1)));


  }

  /**
   * Use this to pass the autonomous command to the main {@link Robot} class.
   *
   * @return the command to run in autonomous
   */
  public Command getAutonomousCommand() {
    // Create config for trajectory
    TrajectoryConfig config =
        new TrajectoryConfig(
                AutoConstants.kMaxSpeedMetersPerSecond,
                AutoConstants.kMaxAccelerationMetersPerSecondSquared)
            // Add kinematics to ensure max speed is actually obeyed
            .setKinematics(DriveConstants.kDriveKinematics);

    // An example trajectory to follow. All units in meters.
    Trajectory exampleTrajectory =
        TrajectoryGenerator.generateTrajectory(
            // Start at the origin facing the +X direction
            Pose2d.kZero,
            // Pass through these two interior waypoints, making an 's' curve path
            List.of(new Translation2d(1, 1), new Translation2d(2, -1)),
            // End 3 meters straight ahead of where we started, facing forward
            new Pose2d(3, 0, Rotation2d.kZero),
            config);

    MecanumControllerCommand mecanumControllerCommand =
        new MecanumControllerCommand(
            exampleTrajectory,
            m_robotDrive::getPose,
            DriveConstants.kFeedforward,
            DriveConstants.kDriveKinematics,

            // Position controllers
            new PIDController(AutoConstants.kPXController, 0, 0),
            new PIDController(AutoConstants.kPYController, 0, 0),
            new ProfiledPIDController(
                AutoConstants.kPThetaController, 0, 0, AutoConstants.kThetaControllerConstraints),

            // Needed for normalizing wheel speeds
            AutoConstants.kMaxSpeedMetersPerSecond,

            // Velocity PID's
            new PIDController(DriveConstants.kPFrontLeftVel, 0, 0),
            new PIDController(DriveConstants.kPRearLeftVel, 0, 0),
            new PIDController(DriveConstants.kPFrontRightVel, 0, 0),
            new PIDController(DriveConstants.kPRearRightVel, 0, 0),
            m_robotDrive::getCurrentWheelSpeeds,
            m_robotDrive::setDriveMotorControllersVolts, // Consumer for the output motor voltages
            m_robotDrive);

    // Reset odometry to the initial pose of the trajectory, run path following
    // command, then stop at the end.
    return Commands.sequence(
        new InstantCommand(() -> m_robotDrive.resetOdometry(exampleTrajectory.getInitialPose())),
        mecanumControllerCommand,
        new InstantCommand(() -> m_robotDrive.drive(0, 0, 0, false)));
  }
}
