import "@testing-library/jest-dom";

// Reset sessionStorage between tests so token-store tests are isolated
beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
});
