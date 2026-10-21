package vn.startupbench;
interface IMeasureService {
    String prepareLaunch(String component, String mode, int userId) = 1;
    String launchOnly(String component, String mode, int userId) = 2;
    void destroy() = 16777114;
}
