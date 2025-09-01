import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import Page, { EXAMPLE_DATA } from './page';

describe('Page', () => {
  render(<Page />);

  it('renders the header and header content correctly', async () => {
    const header = screen.getByRole('banner');
    expect(header).toBeDefined();

    const searchbar = screen.getByRole('textbox', { name: 'Search' });
    expect(searchbar).toBeDefined();

    const searchPlaceholder = screen.getByPlaceholderText('Type to search...');
    expect(searchPlaceholder).toBeDefined();

    const linkToHome = screen.getByRole('link', { name: '' });
    expect(linkToHome).toBeDefined();
  });

  it('renders main with the sidebar correctly', async () => {
    const main = screen.getByRole('main');
    expect(main).toBeDefined();

    const sidebarParentItems = screen.getAllByRole('list');

    expect(sidebarParentItems.length).toEqual(3);

    EXAMPLE_DATA.navMain.forEach((item) => {
      const sideBarParentItemTitle = screen.getByText(item.title);
      expect(sideBarParentItemTitle).toBeDefined();
    });

    EXAMPLE_DATA.navMain.forEach((item) => {
      const sideBarParentItemLink = screen.getByRole('link', {
        name: item.title,
      });
      expect(sideBarParentItemLink).toBeDefined();
    });

    EXAMPLE_DATA.navMain.forEach((item) =>
      item.items?.forEach((subitem) => {
        const sideBarSubItemTitle = screen.getByText(subitem.title);
        expect(sideBarSubItemTitle).toBeDefined();
      }),
    );

    EXAMPLE_DATA.navMain.forEach((item) =>
      item.items?.forEach((subitem) => {
        const sideBarSubItemLink = screen.getByRole('link', {
          name: subitem.title,
        });
        expect(sideBarSubItemLink).toBeDefined();
      }),
    );

    const menuItemsToggleButtons = screen.getAllByRole('button', {
      name: /toggle/i,
    });

    expect(menuItemsToggleButtons.length).toEqual(2);

    const loggedInUser = screen.getByRole('button', {
      name: `CN ${EXAMPLE_DATA.user.name} ${EXAMPLE_DATA.user.email}`,
    });

    expect(loggedInUser).toBeDefined();
  });
});
