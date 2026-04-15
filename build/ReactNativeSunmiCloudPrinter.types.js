export class SunmiError extends Error {
    code;
    message;
    constructor(code, message) {
        super();
        this.name = 'SunmiError';
        this.code = code;
        this.message = message;
    }
}
//# sourceMappingURL=ReactNativeSunmiCloudPrinter.types.js.map