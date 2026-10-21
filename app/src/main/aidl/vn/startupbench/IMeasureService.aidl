package vn.startupbench;
interface IMeasureService {
    String measure(String component, String mode, int userId) = 0;
    void destroy() = 16777114;
}
