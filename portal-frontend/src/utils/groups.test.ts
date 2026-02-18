import groups from '@/__mocks__/groups/groupsResponse.json'

//TODO: reimplement this test when flattengroups for subgroups is implemented
describe.skip('flattenGroups', () => {
  it('should flatten groups and subgroups into a single array', () => {
    const expected = [
      {
        id: '1',
        title: 'Group 1',
        description: 'description for Group 1',
        roles: [],
        users: [
          { id: 'user1', assignedAt: '' },
          { id: 'user2', assignedAt: '' },
        ],
        contact: { id: 'c1', displayName: 'Contact 1' },
        parent: null,
        dataspace: null,
        subgroups: [],
      },
      {
        id: '2',
        title: 'SubGroup 1-1',
        description: 'description for SubGroup 1-1',
        roles: [],
        users: [],
        contact: null,
        parent: '1',
        dataspace: null,
        subgroups: [],
      },
      {
        id: '3',
        title: 'SubGroup 1-1-1',
        description: 'description for SubGroup 1-1-1',
        roles: [],
        users: [],
        contact: null,
        parent: '2',
        dataspace: null,
        subgroups: [],
      },
      {
        id: '4',
        title: 'SubGroup 1-1-2',
        description: 'description for SubGroup 1-1-2',
        roles: [],
        users: [],
        contact: null,
        parent: '2',
        dataspace: null,
        subgroups: [],
      },
      {
        id: '5',
        title: 'SubGroup 1-2',
        description: 'description for SubGroup 1-2',
        roles: [],
        users: [],
        contact: null,
        parent: '1',
        dataspace: null,
        subgroups: [],
      },
      {
        id: '6',
        title: 'Group 2',
        description: 'description for Group 2',
        roles: [],
        users: [],
        contact: { id: 'c2', displayName: 'Contact 2' },
        parent: null,
        dataspace: null,
        subgroups: [],
      },
    ]

    // const result = flattenGroups(groups)

    // expect(result).toEqual(expected)
  })
})
